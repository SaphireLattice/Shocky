import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;
import org.pircbotx.ShockyBot;
import org.pircbotx.User;
import org.pircbotx.hooks.events.MessageEvent;

import pl.shockah.shocky.Data;
import pl.shockah.shocky.Module;
import pl.shockah.shocky.Shocky;
import pl.shockah.shocky.interfaces.ILua;
import pl.shockah.shocky.sql.ConnStatResultSet;
import pl.shockah.shocky.sql.CriterionString;
import pl.shockah.shocky.sql.QueryInsert;
import pl.shockah.shocky.sql.QuerySelect;
import pl.shockah.shocky.sql.QueryUpdate;
import pl.shockah.shocky.sql.SQL;
import pl.shockah.shocky.sql.Wildcard;
import pl.shockah.shocky.sql.Criterion.Operation;
import pl.shockah.shocky.sql.CriterionNumber;

public class ModuleHighFiveSQL extends Module implements ILua {
	//private Config config = new Config();
	private HashMap<String,User> started = new HashMap<String,User>();
	private HashMap<String,Long> timers = new HashMap<String,Long>();
	private static final Pattern pattern = Pattern.compile("(\\s|^)(o/|\\\\o)(\\s|$)", Pattern.CASE_INSENSITIVE);
	
	public Pair getPair(Pair p) {
		return getPair(p.nick1, p.nick2, p.id);
	}
	
	public Pair getPair(String n1, String n2, Boolean i1, Boolean i2) {
		Integer im = 0;
		if (i1 == true)
			im = 2;
		if (i2 == true)
			im += 1;
		return getPair(n1, n2, im);
	}
	
	public Pair getPair(String n1, String n2, Integer i) {
		if (n1.compareTo(n2)>0) {
			String temp_s = n1;
			n1 = n2;
			n2 = temp_s;
			if (i == 2)
				i = 1;
			else if (i == 1)
				i = 2;
		}
		Pair ret = null;
		ConnStatResultSet csrs = null;
		ResultSet rs = null;
		try{
			QuerySelect q = new QuerySelect(SQL.getTable("highfive"));
			
			q.addCriterions(new CriterionString("pair",Operation.Equals,n1 + "_" + n2));
			
			if (i != null) {
				q.addOrder("identified", true);
				q.addCriterions(new CriterionNumber("identified",Operation.Equals,i));
			} else
				q.addOrder("identified", false);
			
			q.addOrder("timestamp", false);
			q.setLimitCount(1);
			
			csrs = SQL.select(q,false);
			rs = csrs.rs;
			if (rs != null){
				if (rs.next()) {
					ret = new Pair(n1, n2, rs.getInt("times"));
					ret.timestamp = rs.getLong("timestamp");
					ret.id = rs.getInt("identified");
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		} finally {
			try {
				if (rs != null && !rs.isClosed())
					rs.close();
				csrs.c.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return ret;
	}
	
	public synchronized Boolean updatePair(Pair pa) {
		try {
			if (getPair(pa) != null) {
				QueryUpdate qu = new QueryUpdate(SQL.getTable("highfive"));
				qu.addCriterions(new CriterionString("pair", CriterionNumber.Operation.Equals, pa.toString()));
				qu.addCriterions(new CriterionNumber("identified", CriterionNumber.Operation.Equals, pa.id));
				qu.set("times", pa.times);
				qu.set("timestamp", pa.timestamp);
				SQL.update(qu);
			}
			else {
				QueryInsert qi = new QueryInsert(SQL.getTable("highfive"));
				qi.add("pair",Wildcard.blank);
				qi.add("times",Wildcard.blank);
				qi.add("timestamp",Wildcard.blank);
				qi.add("identified",Wildcard.blank);
				
				Connection tmpc = SQL.getSQLConnection();
				PreparedStatement p = tmpc.prepareStatement(qi.getSQLQuery());
				synchronized (pa) {
					p.setString(1, pa.toString());
					p.setInt(2, pa.times);
					p.setLong(3, pa.timestamp);
					p.setInt(4, pa.id);
					p.execute();
				}
				p.close();
				tmpc.close();
			}
		} catch(Exception e)
		{
			e.printStackTrace();
		}
		return false;
	}
	
	public Pair changeStat(Pair p, Integer change) {
		return changeStat(p.nick1, p.nick2, change, p.id);
	}
	
	public Pair changeStat(String nick1, String nick2, Integer change, Boolean i1, Boolean i2) {
		Integer im = 0;
		if (i1 == true)
			im = 2;
		if (i2 == true)
			im += 1;
		return changeStat(nick1, nick2, change, im);
	}
	
	public Pair changeStat(String nick1, String nick2, Integer change, Integer identified) {
		nick1 = nick1.toLowerCase();
		nick2 = nick2.toLowerCase();
		if (nick1.equals(nick2))
			return null;
		
		Pair p = getPair(nick1, nick2, identified);
		if (p == null) {
			p = new Pair(nick1, nick2, ((identified & 2) == 2), ((identified & 1) == 1));
		}
		
		p.times = p.times+change;
		updatePair(p);
		return p;
	}
	
	public String name() {return "highfivesql";}
	public boolean isListener() {return true;}
	public void onEnable(File dir) {
		Data.config.setNotExists("hf-announce",true);
		Data.config.setNotExists("hf-maxtime",1000*60*5);
		try {
			SQL.raw("CREATE TABLE IF NOT EXISTS highfive (pair text NOT NULL, times INTEGER NOT NULL, timestamp BIG INTEGER(20) NOT NULL, identified int(2) NOT NULL  );");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public void onDisable() {
	}
	public void onDataSave(File dir) {
	}
	
	public void onMessage(MessageEvent<ShockyBot> event) {
		if (Data.isBlacklisted(event.getUser())) return;
		String msg = event.getMessage();
		if (!pattern.matcher(msg).find())
			return;
		
		String chan = event.getChannel().getName();
		Long time = timers.get(chan);
		User s = started.get(chan);
		
		if (s != null && time != null && time < System.currentTimeMillis()) {
			started.remove(chan);
			timers.remove(chan);
			s = null;
		}
		
		if (s == null) {
			time = System.currentTimeMillis()+Data.forChannel(event.getChannel()).getInt("hf-maxtime");
			started.put(chan,event.getUser());
			timers.put(chan,time);
		} else {
			String id_s = Shocky.getLogin(s);
			String id = Shocky.getLogin(event.getUser());
			Integer identified = 0;
			String nick_s = s.getNick();
			if (id_s != null) {
				identified = 2;
				nick_s = id_s;
			}
			String nick = event.getUser().getNick();
			if (id != null){
				identified += 1;
				nick = id;
			}
			
			Pair p = new Pair(nick_s, nick, ((identified & 2) == 2), ((identified & 1) == 1));
			
			p = changeStat(p, 1);
			if (p.times != 0) {
				msg = s.getNick()+" o/ * \\o "+event.getUser().getNick()+" - "+getOrderNumber(p.times)+" time";
				if (Data.forChannel(event.getChannel()).getBoolean("hf-announce"))
					Shocky.sendChannel(event.getBot(),event.getChannel(),msg);
				else {
					Shocky.sendNotice(event.getBot(),event.getUser(),msg);
					Shocky.sendNotice(event.getBot(),s,msg);
				}
				
				started.remove(chan);
				timers.remove(chan);
			}
		}
	}
	
	public String getOrderNumber(int n) {
		StringBuilder sb = new StringBuilder(3);
		sb.append(n);
		int n100 = n % 100;
		if (n100 >= 10 && n100 < 20) {
			sb.append("th");
		} else {
			switch (n % 10) {
			case 1: sb.append("st"); break;
			case 2: sb.append("nd"); break;
			case 3: sb.append("rd"); break;
			default: sb.append("th"); break;
			}
		}
		return sb.toString();
	}
	
	public class Pair {
		public String nick1;
		public String nick2;
		public Integer times = 0;
		public Integer id = 0;
		public Long timestamp = (System.currentTimeMillis() / 1000L);
		
		public Pair(String n1, String n2) {
			if (n1.compareTo(n2)>0) {
				String temp = n1;
				n1 = n2;
				n2 = temp;
			}
			this.nick1 = n1;
			this.nick2 = n2;
		}
		
		public Pair(String n1, String n2, Integer t) {
			if (n1.compareTo(n2)>0) {
				String temp = n1;
				n1 = n2;
				n2 = temp;
			}
			this.nick1 = n1;
			this.nick2 = n2;
			this.times = t;
		}
		
		public Pair(String n1, String n2, Boolean i1, Boolean i2) {
			if (n1.compareTo(n2)>0) {
				String temp_s = n1;
				Boolean temp_b = i1;
				n1 = n2;
				i1 = i2;
				n2 = temp_s;
				i2 = temp_b;
			}
			this.nick1 = n1;
			this.nick2 = n2;
			if (i1 == true)
				this.id = 2;
			if (i2 == true)
				this.id += 1;
		}
		public String toString() {
			return nick1 + "_" + nick2;
		}
	}
	
	public class Function extends TwoArgFunction {

		@Override
		public LuaValue call(LuaValue arg1, LuaValue arg2) {
			String nick1 = arg1.checkjstring().toLowerCase();
			String nick2 = arg2.checkjstring().toLowerCase();
			
			Pair tmp = getPair(nick1, nick2, null);
			return valueOf((tmp != null)?tmp.times:0);
		}
	}

	@Override
	public void setupLua(LuaTable env) {
		env.set("hf", new Function());
	}
}