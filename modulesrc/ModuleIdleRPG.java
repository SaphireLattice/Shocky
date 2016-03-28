import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.ShockyBot;
import org.pircbotx.User;
import org.pircbotx.hooks.events.MessageEvent;

import pl.shockah.shocky.Data;
import pl.shockah.shocky.Module;
import pl.shockah.shocky.Shocky;
import pl.shockah.shocky.Utils;
import pl.shockah.shocky.cmds.Command;
import pl.shockah.shocky.interfaces.ILua;
import pl.shockah.shocky.interfaces.IPaste;
import pl.shockah.shocky.sql.ConnStatResultSet;
import pl.shockah.shocky.sql.CriterionNumber;
import pl.shockah.shocky.sql.Criterion.Operation;
import pl.shockah.shocky.sql.CriterionString;
import pl.shockah.shocky.sql.QueryInsert;
import pl.shockah.shocky.sql.QuerySelect;
import pl.shockah.shocky.sql.QueryUpdate;
import pl.shockah.shocky.sql.SQL;
import pl.shockah.shocky.sql.Wildcard;

public class ModuleIdleRPG extends Module implements ILua {
	public static DecimalFormat formatXP = new DecimalFormat("###,###", new DecimalFormatSymbols(Locale.ENGLISH));
	public static DecimalFormat formatXPPercent = new DecimalFormat("###,###.#", new DecimalFormatSymbols(Locale.ENGLISH));
	public static final String fmname = "idlerpg";
	public String name() {
		return fmname;
	}

	public boolean isListener() {
		return true;
	}

	public void onEnable(File dir) {
		Data.config.setNotExists("idlerpg-channel", "#ssss");
		Data.config.setNotExists("idlerpg-announce", true);
		Data.config.setNotExists("idlerpg-leaderboards-print", 10);
		if (!Data.protectedKeys.contains("idlerpg-channel"))
			Data.protectedKeys.addAll(Arrays.asList(new String[] {"idlerpg-channel", "idlerpg-announce", "idlerpg-leaderboards-print" }));
		try {
			SQL.raw("idlerpg","CREATE TABLE IF NOT EXISTS idlerpg (name text NOT NULL, level INTEGER NOT NULL, xp BIG INTEGER(20) NOT NULL, lastupdate BIG INTEGER(20) NOT NULL);");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Player GetPlayerFromSQL(String identify) {
		Player ret = null;
		ConnStatResultSet csrs = null;
		ResultSet rs = null;
		try{
			QuerySelect q = new QuerySelect(SQL.getTable("idlerpg"));
			q.addCriterions(new CriterionString("name",Operation.Equals,identify));
			q.setLimitCount(1);
			csrs = SQL.select(q,false,fmname);
			rs = csrs.rs;
			if (rs != null){
				if (rs.next()) {
					ret = new Player(identify);
					ret.level = rs.getInt("level");
					ret.xp = rs.getInt("xp");
					ret.lastUpdate = rs.getLong("lastupdate");
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
	
	public static synchronized void UpdatePlayerToSQL(String identify, Player player) {
		if(player==null)
			return;
		try{
			if (GetPlayerFromSQL(identify) != null) {
				QueryUpdate qu = new QueryUpdate(SQL.getTable("idlerpg"));
				qu.addCriterions(new CriterionString("name", CriterionNumber.Operation.Equals, identify));
				qu.set("level", player.level);
				qu.set("xp", player.xp);
				qu.set("lastupdate", player.lastUpdate);
				SQL.update(qu,fmname);
			}
			else {
				QueryInsert qi = new QueryInsert(SQL.getTable("idlerpg"));
				qi.add("name",Wildcard.blank);
				qi.add("level",Wildcard.blank);
				qi.add("xp",Wildcard.blank);
				qi.add("lastupdate",Wildcard.blank);
				
				Connection tmpc = SQL.getSQLConnection(fmname);
				PreparedStatement p = tmpc.prepareStatement(qi.getSQLQuery());
				synchronized (p) {
					p.setString(1, identify);
					p.setInt(2, player.level);
					p.setInt(3, player.xp);
					p.setLong(4, player.lastUpdate);
					p.execute();
				}
				p.close();
				tmpc.close();
			}
		} catch(Exception e)
		{
			e.printStackTrace();
		}
		return;
	}
	
	public static void send(MessageEvent<ShockyBot> ev, String message) {
		Command.EType type = Command.EType.Channel;
		String allowedChannel = Data.config.getString("idlerpg-channel");
		if ((!allowedChannel.isEmpty())
				&& (!allowedChannel.equalsIgnoreCase(ev.getChannel().getName())))
			type = Command.EType.Notice;

		Shocky.send(ev.getBot(), type, ev.getChannel(), ev.getUser(), message);
	}

	public static void send(Session session, String message) {
		Command.EType type = Command.EType.Channel;
		String allowedChannel = Data.config.getString("idlerpg-channel");
		if ((!allowedChannel.isEmpty())
				&& (!allowedChannel.equalsIgnoreCase(session.channel.getName())))
			type = Command.EType.Notice;

		Shocky.send(session.bot, type, session.channel, session.user, message);
	}

	public static void announce(MessageEvent<ShockyBot> ev, String message) {
		Command.EType type = Command.EType.Channel;
		String allowedChannel = Data.config.getString("idlerpg-channel");
		if ((!allowedChannel.isEmpty())
				&& (!allowedChannel.equalsIgnoreCase(ev.getChannel().getName()))
				&& (!Data.config.getBoolean("idlerpg-announce")))
			type = Command.EType.Notice;

		Shocky.send(ev.getBot(), type, ev.getChannel(), ev.getUser(), message);
	}

	public static void announce(Session session, String message) {
		Command.EType type = Command.EType.Channel;
		String allowedChannel = Data.config.getString("idlerpg-channel");
		if ((!allowedChannel.isEmpty())
				&& (!allowedChannel.equalsIgnoreCase(session.channel.getName()))
				&& (!Data.config.getBoolean("idlerpg-announce")))
			type = Command.EType.Notice;

		Shocky.send(session.bot, type, session.channel, session.user, message);
	}

	public void onMessage(MessageEvent<ShockyBot> ev) {
		if (Data.isBlacklisted(ev.getUser()))
			return;

		String msg = ev.getMessage();
		if (!msg.startsWith(">"))
			return;

		String identify = Shocky.getLogin(ev.getUser());
		if (identify == null) {
			send(ev, ev.getUser().getNick()+": You need to be identified to NickServ to play IdleRPG.");
			return;
		}
		
		Session session = new Session(ev.getBot(), ev.getChannel(), ev.getUser(), identify);
		//session.player = this.players.get(identify);
		session.player = GetPlayerFromSQL(identify);
		
		msg = msg.substring(1).trim();
		String[] spl = msg.isEmpty() ? new String[0] : msg.split(" ");
		if ((spl.length == 0)
				|| ((spl.length <= 2) && ("status".startsWith(spl[0]
						.toLowerCase()) )) ) {
			Player check = session.player;
			if (spl.length == 2)
				check = GetPlayerFromSQL(spl[1]);

			if (check == null) {
				if (spl.length == 2) {
					send(ev, ev.getUser().getNick() + ": Player '" + spl[1]
							+ "' doesn't exist.");
					return;
				}
				UpdatePlayerToSQL(identify, new Player(identify));
				send(ev, ev.getUser().getNick() + ": Welcome to the IdleRPG, "
						+ identify + '!');
				return;
			}

			check.update(session);
			UpdatePlayerToSQL(check.name , check);
			send(ev, check.printStatus(session, true, true));
		} else if ((spl.length >= 1)
				&& ("leaderboards".startsWith(spl[0].toLowerCase())) ) {
			//DONE: rewrite leaderboards to use SQL
			
			int maxPrint = Data.config.getInt("idlerpg-leaderboards-print");
			try{
				if (spl.length >= 2)
						maxPrint = Integer.parseInt(spl[1]);}
			catch (ArrayIndexOutOfBoundsException e){
			}
			StringBuilder print = new StringBuilder();
			StringBuilder paste = new StringBuilder();
			
			ConnStatResultSet csrs = null;
			ResultSet rs = null;
			Integer i = -1;
			try{
				QuerySelect q = new QuerySelect(SQL.getTable("idlerpg"));
				q.addOrder("level",false);
				q.addOrder("xp",false);
				//q.setLimitCount(maxPrint);
				csrs = SQL.select(q,false,"idlerpg");
				rs = csrs.rs;
				if (rs != null){
					while(rs.next()) {
						Player p = new Player(rs.getString("name"), rs.getInt("level"), rs.getInt("xp"), rs.getLong("lastupdate"));
						
						i++;
						if (i != 0) {
							if (i < maxPrint)
								print.append(" | ");
							paste.append('\n');
						}
						
						if (i < maxPrint)
							print.append(i + 1).append(". ").append(p.printStatus(session, false, false, false));
						paste.append(i + 1).append(". ").append(p.printStatus(session, true, false, false));
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
			
			if (i > maxPrint) {
				try{
					IPaste p = (IPaste) Module.getModule("paste");
					if (p!=null){
						String url = p.paste(paste);
						if (url != null)
							if  (!url.isEmpty())
								print.append(" | Full leaderboards: ").append(url);
							else 
								print.append(" | Paste failed: url is empty");
						else
							print.append(" | Paste failed: url is null");
					} else
						print.append(" | Paste module not loaded");
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
			send(ev, Utils.mungeAllNicks(ev.getChannel(), 0, print, ev.getUser()));
			//for (Player p : this.players.values())
			//	p.update(session);

			/*
			ArrayList<Player> list = new ArrayList<Player>(this.players.values());
			
			Collections.sort(list, new ComparatorLevel(false));

			for (int i = 0; i < list.size(); i++) {
				if (i != 0) {
					if (i < maxPrint)
						print.append(" | ");
					paste.append('\n');
				}

				Player p = (Player) list.get(i);
				if (i < maxPrint)
					print.append(i + 1).append(". ")
					.append(p.printStatus(session, list.size() <= maxPrint, false));
				paste.append(i + 1).append(". ").append(p.printStatus(session, true, false));
			}
			if (list.size() > maxPrint) {
				String url = Utils.paste(paste);
				if ((url != null) && (!url.isEmpty()))
					print.append(" | Full leaderboards: ").append(url);
			}/**/
		}
	}

	public static class Player {
		public String name;
		public int level;
		public int xp;
		public long lastUpdate;
		
		private static final Map<Integer,Integer> xpTable = new HashMap<Integer,Integer>();
		public static int getXPForLevel(int level) {
			if (level <= 1)
				return 0;
			if (xpTable.containsKey(level))
				return xpTable.get(level);
			long a = 0L;
			for (int x = 1; x < level; ++x)
				a += (int) (x + 300.0D * Math.pow(2.0D, x / 7.0D));
			int value = (int) (a / 4.0D);
			xpTable.put(level, value);
			return value;
		}
		
		public int getXPForNextLevel() {
			return getXPForLevel(this.level + 1);
		}

		public static String printBar(double value, int length) {
			double f = 1.0D / length;
			char[] c = new char[length + 2];
			int i = 0;
			c[i++]='[';
			for (; i <= length; ++i)
				c[i]=(value >= i * f) ? '=' : ' ';
			c[i]=']';
			return new String(c);
		}

		public Player(String name, Integer level, Integer xp, Long lastUpdate) {
			this.name = name;
			this.level = level;
			this.xp = xp;
			this.lastUpdate = lastUpdate;
		}
		
		public Player(String name) {
			this.name = name;
			this.level = 1;
			this.xp = 0;
			this.lastUpdate = (System.currentTimeMillis() / 1000L);
		}
		
		public void update(Session session) {
			long time = System.currentTimeMillis() / 1000L;
			long diff = time - this.lastUpdate;
			this.lastUpdate = time;

			int xp2l = getXPForNextLevel();
			this.xp = (int) (this.xp + diff);
			if ((session != null) && (session.player == this)
					&& (this.xp >= xp2l)) {
				this.xp = 0;
				this.level += 1;
			}

			if (this.xp > xp2l)
				this.xp = xp2l;
		}
		
		public String printStatus(Session session, boolean printXP, boolean printTime) {
			return printStatus(session, printXP, printTime, true);
		}
		
		public String printStatus(Session session, boolean printXP, boolean printTime, Boolean announce) {
			StringBuilder sb = new StringBuilder();
			sb.append(this.name);
			if ((session.identify.equalsIgnoreCase(this.name)) && (!session.user.getNick().equalsIgnoreCase(this.name)))
				sb.append(" / ").append(session.user.getNick());
			sb.append(", level ").append(this.level);

			int xp2l = getXPForNextLevel();
			if (printXP) {
				if ((this.level != 1) && (this.xp == 0)) {
					sb.append(", LEVEL UP!");

					if (this.level % 5 == 0) {
						StringBuilder sb2 = new StringBuilder();
						sb2.append(">>> CONGRATULATIONS! ").append(this.name);
						if ((session.identify.equalsIgnoreCase(this.name))
								&& (!session.user.getNick()
										.equalsIgnoreCase(this.name)))
							sb2.append(" / ").append(session.user.getNick());
						sb2.append(" achieved level ").append(this.level).append("! <<<");
						if(announce)
							ModuleIdleRPG.announce(session, sb2.toString());
					}
				} else if (this.xp == xp2l)
					sb.append(", level up available");
				else 
					sb.append(", XP: ").append(formatXP.format(this.xp)).append(" / ").append(formatXP.format(xp2l));

				if ((this.xp != 0) && (this.xp != xp2l))
					sb.append(' ')
					.append(printBar(1.0D * this.xp / xp2l, 20))
					.append(" (").append(formatXPPercent.format(100.0D * this.xp / xp2l)).append("%)");
			}
			if ((printTime) && (this.xp != xp2l))
				sb.append(" | ").append(Utils.timeAgo(xp2l - this.xp)).append(" until level up");
			return sb.toString();
		}
	}
	
	public static class ComparatorLevel implements Comparator<Player> {
		public final int dir;
		public ComparatorLevel(boolean asc) {
			dir = asc ? 1 : -1;
		}
		public int compare(Player p1, Player p2) {
			if (p1.level != p2.level)
				return p1.level < p2.level ? -dir : dir;
			if (p1.xp != p2.xp)
				return p1.xp < p2.xp ? -dir : dir;
			return 0;
		}
	}
	
	public static class ComparatorIgnoreCase implements Comparator<String> {
		public int compare(String s1, String s2) {
			return s1.compareToIgnoreCase(s2);
		}
	}

	public static class Session {
		public final PircBotX bot;
		public final Channel channel;
		public final User user;
		public final String identify;
		public Player player;

		public Session(PircBotX bot, Channel channel, User user, String identify) {
			this.bot = bot;
			this.channel = channel;
			this.user = user;
			this.identify = identify;
		}
	}
	
	public LuaValue getPlayerTable(String name) {
		Player player = GetPlayerFromSQL(name);
		if (player==null)
			return LuaValue.NIL;
		player.update(null);
		return getPlayerTable(player);
	}
	
	public LuaValue getPlayerTable(Player player) {
		LuaTable t = new LuaTable();
		t.rawset("name", player.name);
		t.rawset("xp", player.xp);
		t.rawset("need", player.getXPForNextLevel());
		t.rawset("level", player.level);
		t.rawset("lastUpdate", player.lastUpdate);
		return t;
	}
	
	public class StatusFunction extends OneArgFunction {

		@Override
		public LuaValue call(LuaValue arg) {
			return getPlayerTable(arg.checkjstring());
		}
	}
	
	/*public class LeaderboardFunction extends ZeroArgFunction {

		@Override
		public LuaValue call() {
			int maxPrint = Data.config.getInt("idlerpg-leaderboards-print");
			List<Player> list = new ArrayList<Player>(players.values());
			
			for (Player p : list)
				p.update(null);
			Collections.sort(list, new ComparatorLevel(false));
			
			LuaTable t = new LuaTable();
			for (int i = 0; i < maxPrint && i < list.size(); ++i)
				t.rawset(i+1, getPlayerTable(list.get(i)));
			return t;
		}
	}*/

	@Override
	public void setupLua(LuaTable env) {
		try {
			Class.forName("ModuleIdleRPG$Player");
			Class.forName("ModuleIdleRPG$ComparatorLevel");
		} catch (Exception e) {
			e.printStackTrace();
		}
		LuaTable t = new LuaTable();
		t.rawset("status", new StatusFunction());
		//t.rawset("leaders", new LeaderboardFunction());
		env.set("idlerpg", t);
	}
}