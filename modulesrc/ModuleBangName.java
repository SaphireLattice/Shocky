import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import pl.shockah.StringTools;
import pl.shockah.shocky.Module;
import pl.shockah.shocky.Shocky;
import pl.shockah.shocky.Utils;
import pl.shockah.shocky.cmds.Command;
import pl.shockah.shocky.cmds.CommandCallback;
import pl.shockah.shocky.cmds.Parameters;
import pl.shockah.shocky.sql.ConnStatResultSet;
import pl.shockah.shocky.sql.CriterionNumber;
import pl.shockah.shocky.sql.CriterionString;
import pl.shockah.shocky.sql.QueryInsert;
import pl.shockah.shocky.sql.QuerySelect;
import pl.shockah.shocky.sql.QueryUpdate;
import pl.shockah.shocky.sql.SQL;
import pl.shockah.shocky.sql.Wildcard;
import pl.shockah.shocky.sql.Criterion.Operation;

public class ModuleBangName extends Module {
	protected Command cmd, cmdAdd, cmdRemove, cmdAlias;

	public String name() {return "bangname";}
	public void onEnable(File dir) {
		try {
			SQL.raw("CREATE TABLE IF NOT EXISTS bangnames (gid INTEGER PRIMARY KEY AUTOINCREMENT, name text NOT NULL, channel text NOT NULL, bang text, removed integer(1) not null default 0, timestamp BIG INTEGER(20) NOT NULL);");
			SQL.raw("CREATE TABLE IF NOT EXISTS bangaliases (name text not null, alias text not null, l_alias text not null, main integer(1) not null default 1, removed integer(1) not null default 0, timestamp big integer(20) not null);");
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		Command.addCommands(this, cmd = new CmdBangName(), cmdAdd = new CmdBangAdd(), cmdRemove = new CmdBangRemove(), cmdAlias = new CmdBangAlias());
		Command.addCommand(this, "bname", cmd);
		Command.addCommand(this, "badd", cmdAdd);
		Command.addCommand(this, "bdel", cmdRemove);
		Command.addCommand(this, "balias", cmdAlias);
	}
	public void onDisable() {
		Command.removeCommands(cmd, cmdAdd, cmdRemove, cmdAlias);
	}
	public void onDataSave(File dir) {
	}
	
	//SQL FUNCTIONS
	//Important: bang ID is not written (except for the global one), sort by date - oldest first, has nick (the Q identity) and "removed" fields 
	//-------------
	Integer getBangsAmount(String channel, String name) {
		Integer ret = 0;
		ConnStatResultSet csrs = null;
		ResultSet rs = null;
		name = getAlias(name);
		try{
			QuerySelect q = new QuerySelect(SQL.getTable("bangnames"));
			
			q.addCriterions(new CriterionString("name",Operation.Equals,name));
			if (channel != null)
				q.addCriterions(new CriterionString("channel", Operation.Equals, channel));
			q.addCriterions(new CriterionNumber("removed", Operation.Equals,0));
			q.addOrder("timestamp", true);
			
			csrs = SQL.select(q,false);
			rs = csrs.rs;
			if (rs != null){
				while (rs.next()) {
					ret += 1;
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
	
	Integer getBangGID(String channel, String name, Integer id) {
		Integer ret = null;
		Integer num = 0;
		ConnStatResultSet csrs = null;
		ResultSet rs = null;
		name = getAlias(name);
		try{
			QuerySelect q = new QuerySelect(SQL.getTable("bangnames"));
			
			q.addCriterions(new CriterionString("name",Operation.Equals,name));
			if (channel != null)
				q.addCriterions(new CriterionString("channel", Operation.Equals, channel));
			q.addCriterions(new CriterionNumber("removed", Operation.Equals,0));
			q.addOrder("timestamp", true);
			
			csrs = SQL.select(q,false);
			rs = csrs.rs;
			if (rs != null){
				while (rs.next()) {
					num += 1;
					if (num == id)
						ret = rs.getInt("gid");
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

	BangName selectBang(String channel, String name, Integer id) {
		BangName ret = null;
		ConnStatResultSet csrs = null;
		ResultSet rs = null;
		name = getAlias(name);
		try{
			QuerySelect q = new QuerySelect(SQL.getTable("bangnames"));
			if (name != null)
				q.addCriterions(new CriterionString("name",Operation.Equals,name));
			if (channel != null)
				q.addCriterions(new CriterionString("channel", Operation.Equals, channel));
			if (id != null) {
				if (name == null && channel == null)
					q.addCriterions(new CriterionNumber("gid",Operation.Equals, id));
				else
					q.addCriterions(new CriterionNumber("gid",Operation.Equals, getBangGID(channel, name, id)));
			}
			q.addCriterions(new CriterionNumber("removed", Operation.Equals,0));
			q.addOrder("timestamp", false);
			q.setLimitCount(1);
			
			csrs = SQL.select(q,false);
			rs = csrs.rs;
			if (rs != null){
				if (rs.next()) {
					ret = new BangName(rs.getString("name"),rs.getString("bang"), rs.getInt("gid"), rs.getLong("timestamp"));
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
	
	void insertBang(String channel, String name, String quote) {
		name = getAlias(name);
		try {
			QueryInsert qi = new QueryInsert(SQL.getTable("bangnames"));
			qi.add("channel",Wildcard.blank);
			qi.add("name",Wildcard.blank);
			qi.add("bang",Wildcard.blank);
			qi.add("timestamp",Wildcard.blank);
			
			Connection tmpc = SQL.getSQLConnection();
			PreparedStatement p = tmpc.prepareStatement(qi.getSQLQuery());
			synchronized (p) {
				p.setString(1, channel);
				p.setString(2, name);
				p.setString(3, quote);
				p.setLong(4, System.currentTimeMillis() / 1000L);
				p.execute();
			}
			p.close();
			tmpc.close();
		} catch(Exception e)
		{
			e.printStackTrace();
		}
		return;
	}
	
	int removeBang(String channel, String nick, Integer id) {
		Integer ret = 1; //Not found
		Integer gid = id;
		try{
			if (channel != null || nick != null);
				gid = getBangGID(channel, nick, id);
			if (gid != null) {
				QueryUpdate qu = new QueryUpdate(SQL.getTable("bangnames"));
				qu.addCriterions(new CriterionNumber("removed", Operation.Equals, 0));
				qu.addCriterions(new CriterionNumber("id", Operation.Equals, gid));
				qu.set("removed", true);
				SQL.update(qu);
				ret = 0;
			}
		} catch(Exception e)
		{
			ret = 3; //java or SQL error
			e.printStackTrace();
		}
		return ret;
	}
	
	String getAlias(String nick) {
		String ret = nick;
		ConnStatResultSet csrs = null;
		ResultSet rs = null;
		try{
			QuerySelect q = new QuerySelect(SQL.getTable("bangaliases"));
			q.addCriterions(new CriterionString("l_alias",Operation.Equals,nick.toLowerCase()));
			q.addCriterions(new CriterionNumber("removed", Operation.Equals,0));
			q.addOrder("timestamp", false);
			q.setLimitCount(1);
			
			csrs = SQL.select(q,false);
			rs = csrs.rs;
			if (rs != null){
				if (rs.next()) {
					ret = rs.getString("name");
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
	
	String getMainAlias(String origin) {
		String ret = origin;
		ConnStatResultSet csrs = null;
		ResultSet rs = null;
		try{
			QuerySelect q = new QuerySelect(SQL.getTable("bangaliases"));
			q.addCriterions(new CriterionString("name",Operation.Equals,origin));
			q.addCriterions(new CriterionNumber("main", Operation.Equals,1));
			q.addCriterions(new CriterionNumber("removed", Operation.Equals,0));
			q.addOrder("timestamp", false);
			q.setLimitCount(1);
			
			csrs = SQL.select(q,false);
			rs = csrs.rs;
			if (rs != null){
				if (rs.next()) {
					ret = rs.getString("alias");
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
	
	void addAlias(String target, String origin) {
		if (target.equals(origin))
			return;
		try {
			QueryInsert qi = new QueryInsert(SQL.getTable("bangaliases"));
			qi.add("alias",Wildcard.blank);
			qi.add("l_alias",Wildcard.blank);
			qi.add("name",Wildcard.blank);
			qi.add("timestamp",Wildcard.blank);
			
			Connection tmpc = SQL.getSQLConnection();
			PreparedStatement p = tmpc.prepareStatement(qi.getSQLQuery());
			synchronized (p) {
				p.setString(1, target);
				p.setString(2, target.toLowerCase());
				p.setString(3, origin.toLowerCase());
				p.setLong(4, System.currentTimeMillis() / 1000L);
				p.execute();
			}
			p.close();
			tmpc.close();
		} catch(Exception e)
		{
			e.printStackTrace();
		}
		return;
	}
	
	Map<Integer,String> getAliases(String origin) {
		HashMap<Integer, String> ret = new HashMap<Integer,String>();
		ret.put(1,origin);
		ConnStatResultSet csrs = null;
		ResultSet rs = null;
		try{
			QuerySelect q = new QuerySelect(SQL.getTable("bangaliases"));
			q.addCriterions(new CriterionString("name",Operation.Equals,origin.toLowerCase()));
			q.addCriterions(new CriterionNumber("removed", Operation.Equals,0));
			q.addOrder("timestamp", false);
			
			csrs = SQL.select(q,false);
			rs = csrs.rs;
			if (rs != null){
				while (rs.next()) {
					ret.put(ret.size()+1,rs.getString("alias"));
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
	
	void removeAlias(String target, String name) {
		try{
			QueryUpdate qu = new QueryUpdate(SQL.getTable("bangaliases"));
			qu.addCriterions(new CriterionNumber("removed", Operation.Equals, 0));
			qu.addCriterions(new CriterionString("l_alias", Operation.Equals, target.toLowerCase()));
			qu.addCriterions(new CriterionString("name", Operation.Equals, name.toLowerCase()));
			qu.set("removed", 1);
			SQL.update(qu);
		} catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	//END
	
	public class CmdBangName extends Command {
		public String command() {return "bangname";}
		public String help(Parameters params) {
			StringBuilder sb = new StringBuilder();
			sb.append("bname");
			sb.append("\nbname [channel] [nick] [id] - shows a bang (!name) message, defaults to your nickname as argument");
			return sb.toString();
		}
		
		public void doCommand(Parameters params, CommandCallback callback) {
			if (params.tokenCount==0 && params.type != EType.Channel) {
				callback.type = EType.Notice;
				callback.append(help(params));
				return;
			}
			
			String aChannel = params.type == EType.Channel ? params.channel.getName() : null, aNick = null;
			Integer aId = null;
			
			String login = Shocky.getLogin(params.sender);
			boolean loggedIn = login != null && !login.isEmpty();
			if (params.tokenCount>=1) {
				String par1 = params.nextParam();
				if (par1.charAt(0) == '#')
					aChannel = par1;
				else if (StringTools.isNumber(par1) == false)
					aNick = par1;
				else
					aId = Integer.parseInt(par1);
				if (params.tokenCount>=2) {
					String par2 = params.nextParam();
					if (StringTools.isNumber(par2) == false)
						aNick = par2;
					else
						aId = Integer.parseInt(par2);
					if (params.tokenCount==3) {
						String par3 = params.nextParam();
						if (StringTools.isNumber(par3))
							aId = Integer.parseInt(par3);
					}
				}
			}
			
			if (aNick == null)
				aNick = loggedIn ? login : params.sender.getNick();
			aNick = aNick.toLowerCase();

			Integer bamount = getBangsAmount(aChannel,aNick);	
			if (bamount == 0) {
				callback.append("No quotes found");
				return;
			}
			
			BangName bang = selectBang(aChannel,aNick,aId);
			
			String quote = Utils.mungeAllNicks(params.channel, 0, bang.quote);
			callback.append(getMainAlias(bang.nick))
			.append(' ')
			.append(quote)
			.append(' ')
			.append('(')
			.append(bang.gid)
			.append(')');
		}
	}
	
	public class CmdBangAdd extends Command {
		public String command() {return "bangadd";}
		public String help(Parameters params) {
			StringBuilder sb = new StringBuilder();
			sb.append("badd");
			sb.append("\nbadd {nick} {text} - adds a bang");
			return sb.toString();
		}
		
		public void doCommand(Parameters params, CommandCallback callback) {
			callback.type = EType.Notice;
			if (params.tokenCount < 2) {
				callback.append(help(params));
				return;
			}

			String login = Shocky.getLogin(params.sender);
			boolean loggedIn = login != null && !login.isEmpty();
			if (loggedIn == false) {
				callback.append("Please login to add a bang");
				return;
			}
			
			String channel = params.channel.getName();
			String nick = params.nextParam().toLowerCase();
			String quote = params.getParams(0);
			if (!nick.equals(login.toLowerCase())) {
				params.checkController("You must be a controller to remove bangs of other people.");
			}
			
			insertBang(channel, nick, quote);
			callback.append("Done.");
		}
	}
	
	public class CmdBangRemove extends Command {
		public String command() {return "bangremove";}
		public String help(Parameters params) {
			StringBuilder sb = new StringBuilder();
			sb.append("bremove");
			sb.append("\nbremove [channel] [id] [nick] - removes a bang");
			return sb.toString();
		}
		
		public void doCommand(Parameters params, CommandCallback callback) {
			callback.type = EType.Notice;
			if (params.tokenCount==0 && params.type != EType.Channel) {
				callback.type = EType.Notice;
				callback.append(help(params));
				return;
			}
			String login = Shocky.getLogin(params.sender);
			boolean loggedIn = login != null && !login.isEmpty();
			if (loggedIn == false) {
				callback.append("Please login to remove a bang");
				return;
			}
			
			String aChannel = params.type == EType.Channel ? params.channel.getName() : null, aNick = login;
			Integer aId = null;
			
			if (params.tokenCount>=1) {
				String par1 = params.nextParam();
				if (par1.charAt(0) == '#')
					aChannel = par1;
				else if (StringTools.isNumber(par1))
					aId = Integer.parseInt(par1);
				else
					aNick = par1;
				if (params.tokenCount>=2) {
					String par2 = params.nextParam();
					if (StringTools.isNumber(par2))
						aId = Integer.parseInt(par2);
					else
						aNick = par2;
					if (params.tokenCount==3) {
						String par3 = params.nextParam();
						aNick = par3;
					}
				}
			}
			//aChannel = channel argument/called from
			//aId = quote ID argument
			//aNick = one of the nicks
			if (aChannel == null) {
				callback.append(help(params));
				return;
			}
			
			if (!aNick.equals(login.toLowerCase())) {
				params.checkController("You must be a controller to remove bangs of other people.");
			}
			
			if (aId == null) {
				callback.append("Please specify bang ID.");
				return;
			}
			
			if (aNick != null) aNick = aNick.toLowerCase();
			
			BangName quote = selectBang(aChannel, aNick, aId);
			removeBang(null, null, quote.gid);
			callback.append("Removed quote: ").append(quote.quote);
		}
	}
	
	public class CmdBangAlias extends Command {
		Map<String, Integer> commands = new HashMap<String, Integer>();
		public CmdBangAlias() {
			super();
			commands.put("add", 1);
			commands.put("a", 1);
			commands.put("remove", 2);
			commands.put("rm", 2);
			commands.put("r", 2);
			commands.put("list", 3);
			commands.put("ls", 3);
			commands.put("l", 3);
		}
		
		public String command() {return "bangalias";}
		public String help(Parameters params) {
			StringBuilder sb = new StringBuilder();
			sb.append("balias")
			.append("\nbalias [command] [arg1]  [arg2]... - alias control")
			.append("\n        add      {alias} [name]    - add alias [with target `name`], default if no command and one arg")
			.append("\naliases a")
			.append("\n        remove   {alias}           - removes alias")
			.append("\naliases rm,r")
			.append("\n        list     [nick]            - list aliases, default if no command and args")
			.append("\naliases ls,l");
			return sb.toString();
		}
		
		public void doCommand(Parameters params, CommandCallback callback) {
			int cmd_s = 0;
			callback.type = EType.Notice;
			
			String login = Shocky.getLogin(params.sender);
			boolean loggedIn = login != null && !login.isEmpty();
			
			if (loggedIn == false) {
				callback.append("Please login to use aliases system");
				return;
			}

			String arg1 = "";
			if (params.countParams() > 0){
				arg1 = params.nextParam();
				cmd_s = this.commands.containsKey(arg1) ? this.commands.get(arg1) : 1;
			} else
				cmd_s = 3;

			String alias = "";
			String name = login.toLowerCase();
			switch (cmd_s) {
				//Add
				case 1:
					if (!this.commands.containsKey(arg1) && arg1 != "") {
						alias = arg1;
						if (params.countParams() > 0)
							name = params.nextParam().toLowerCase();
					}else{
						alias = params.nextParam();
						if (params.countParams() > 0)
							name = params.nextParam().toLowerCase();
					}
					if (!name.equals(login.toLowerCase())) {
						params.checkController("You must be a controller to add aliases to other people.");
					}
					if (getAliases(name).containsValue(alias)) {
						callback.append("Alias already exists.");
						return;
					}
					addAlias(alias, name);
					callback.append("Alias '").append(alias).append("' added.");
				break;
				//Remove
				case 2:
					alias = params.nextParam();
					if (params.countParams() > 0)
						name = params.nextParam().toLowerCase();
					
					if (!name.equals(login.toLowerCase())) {
						params.checkController("You must be a controller to remove aliases of other people.");
					}
					
					removeAlias(alias, name);
					callback.append("Alias '").append(alias).append("' removed.");
				break;
				case 3:
					if (!this.commands.containsKey(arg1) && arg1 != "")
						name = arg1;
					else if (params.countParams() > 0)
						name = params.nextParam();
					
					callback.append("Aliases");
					if (!name.equals(login.toLowerCase())) {
						callback.append(" for ").append(getMainAlias(name));
					}
					callback.append(": ");
					callback.append(getAliases(name).values());
				break;
				default:
					callback.append(help(params));
				break;
			}
			return;
		}
	}
	
	public class BangName {
		public Long timestamp;
		public String nick;
		public String quote;
		public Integer gid;
		
		public BangName(String string, String quote, Integer gid, Long timestamp) {
			this.nick = string;
			this.quote = quote;
			this.gid = gid;
			this.timestamp = timestamp;
		}
	}
}