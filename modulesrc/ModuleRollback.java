import java.io.File;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

import org.pircbotx.Channel;
import org.pircbotx.ShockyBot;
import org.pircbotx.hooks.events.*;

import pl.shockah.*;
import pl.shockah.shocky.Data;
import pl.shockah.shocky.Module;
import pl.shockah.shocky.cmds.Command;
import pl.shockah.shocky.cmds.CommandCallback;
import pl.shockah.shocky.cmds.Parameters;
import pl.shockah.shocky.events.*;
import pl.shockah.shocky.interfaces.IRollback;
import pl.shockah.shocky.interfaces.IWebServer;
import pl.shockah.shocky.interfaces.ILinePredicate;
import pl.shockah.shocky.interfaces.IPaste;
import pl.shockah.shocky.lines.*;
import pl.shockah.shocky.sql.*;
import pl.shockah.shocky.sql.QuerySelect;
import pl.shockah.shocky.sql.Criterion.Operation;

import static pl.shockah.shocky.Utils.determineDateFormat;


public class ModuleRollback extends Module implements IRollback {
	private static final Pattern durationPattern = Pattern.compile("-?([0-9]+)([smhdl])", Pattern.CASE_INSENSITIVE);
	private static final SimpleDateFormat sdf = new SimpleDateFormat();
	
	protected Command cmd;
	
	public static final int
		TYPE_MESSAGE = 1,
		TYPE_ACTION = 2,
		TYPE_ENTERLEAVE = 3,
		TYPE_KICK = 4,
		TYPE_MODE = 5,
		TYPE_MESSAGEACTION = 6,
		TYPE_OTHER = 0;
	

	
	public static void appendLines(StringBuilder sb, ArrayList<Line> lines, boolean encode) {
		try {
			for (int i = 0; i < lines.size(); i++) {
				if (i != 0) sb.append('\n');
				String line = toString(lines.get(i));
				if (encode)
					line = URLEncoder.encode(line,"UTF8");
				sb.append(line);
			}
		} catch (Exception e) {e.printStackTrace();}
	}
	public static String toString(Line line) {
		return "["+sdf.format(line.time)+"] "+(Line.getWithChannels() ? "["+line.channel+"] " : " ")+line.getMessage();
	}
	
	public String name() {
		return "rollback";
	}

	public boolean isListener() {
	    return true;
	}

	public void onEnable(File dir) {
		Data.config.setNotExists("rollback-dateformat","dd.MM.yyyy HH:mm:ss");
		sdf.applyPattern(Data.config.getString("rollback-dateformat"));
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		Command.addCommands(this, cmd = new CmdPastebin());
		Command.addCommand(this, "pb", cmd);
		
		SQL.raw("CREATE TABLE IF NOT EXISTS rollback (channel text NOT NULL,users text,type int(1) NOT NULL,stamp BIG INTEGER(20) NOT NULL,text text NOT NULL);");
	}

	public void onDisable() {
		Command.removeCommands(cmd);
	}
	
	public void onMessage(MessageEvent<ShockyBot> event) {
		addRollbackLine(event.getChannel().getName(),new LineMessage(event.getChannel().getName(),event.getUser().getNick(),event.getMessage()));
	}

	public void onMessageOut(MessageOutEvent<ShockyBot> event) {
		addRollbackLine(event.getChannel().getName(),new LineMessage(event.getChannel().getName(),event.getBot().getNick(),event.getMessage()));
	}

	public void onAction(ActionEvent<ShockyBot> event) {
		addRollbackLine(event.getChannel().getName(),new LineAction(event.getChannel().getName(),event.getUser().getNick(),event.getMessage()));
	}

	public void onActionOut(ActionOutEvent<ShockyBot> event) {
		addRollbackLine(event.getChannel().getName(),new LineAction(event.getChannel().getName(),event.getBot().getNick(),event.getMessage()));
	}

	public void onTopic(TopicEvent<ShockyBot> event) {
		if (!event.isChanged()) return;
		addRollbackLine(event.getChannel().getName(),new LineOther(event.getChannel().getName(),"* "+event.getUser().getNick()+" has changed the topic to: "+event.getTopic()));
	}

	public void onJoin(JoinEvent<ShockyBot> event) {
		addRollbackLine(event.getChannel().getName(),new LineEnterLeave(event.getChannel().getName(),event.getUser().getNick(),"("+event.getUser().getHostmask()+") has joined"));
	}

	public void onPart(PartEvent<ShockyBot> event) {
		addRollbackLine(event.getChannel().getName(),new LineEnterLeave(event.getChannel().getName(),event.getUser().getNick(),"("+event.getUser().getHostmask()+") has left"));
	}

	public void onQuit(QuitEvent<ShockyBot> event) {
		for (Channel channel : event.getUser().getChannels()) addRollbackLine(channel.getName(),new LineEnterLeave(channel.getName(),event.getUser().getNick(),"has quit ("+event.getReason()+")"));
	}

	public void onKick(KickEvent<ShockyBot> event) {
		addRollbackLine(event.getChannel().getName(),new LineKick(event));
	}

	public void onNickChange(NickChangeEvent<ShockyBot> event) {
		for (Channel channel : event.getBot().getChannels(event.getUser()))
		    addRollbackLine(
		            channel.getName(),
                    new LineOther(
                            channel.getName(),
                            "* "+event.getOldNick()+" is now known as "+event.getNewNick()
                    )
            );
	}

	public void onMode(ModeEvent<ShockyBot> event) {
		addRollbackLine(event.getChannel().getName(),new LineMode(event));
	}

	public void onUserMode(UserModeEvent<ShockyBot> event) {
		String mode = event.getMode();
		if (mode.charAt(0) == ' ') mode = "+"+mode.substring(1);
		for (Channel channel : event.getBot().getChannels(event.getTarget()))
		    addRollbackLine(
		            channel.getName(),
                    new LineOther(
                            channel.getName(),
                            "* "+event.getSource().getNick()+" sets mode "+mode+" "+event.getTarget().getNick()
                    )
            );
	}
	
	public synchronized void addRollbackLine(String channel, Line line) {
		if (channel == null || !channel.startsWith("#")) return;
		channel = channel.toLowerCase();
		
		PreparedStatement p = null;
		Connection tmpc = SQL.getSQLConnection();
		/*String key = line.getClass().getName();*/
		try {
			QueryInsert q = new QueryInsert(SQL.getTable("rollback"));
			q.add("channel", channel);
			q.add("stamp", System.currentTimeMillis());
			line.fillQuery(q);
			
			p = q.getSQLQuery(tmpc);
			
			synchronized (p) {
				p.execute();
			}
			p.close();
			tmpc.close();
		} catch (SQLException e) {
			if (p != null)
				try {p.close();} catch (SQLException e1) {}
			e.printStackTrace();
		}
	}
	
	public ArrayList<Line> getRollbackLines(String channel, String user, String regex, String cull, boolean newest, int lines, int seconds, int at) {
		return getRollbackLines(Line.class, channel, user, regex, cull, newest, lines, seconds, at);
	}
	
	public Line getLine(ResultSet result) throws SQLException {
		switch (result.getInt("type")) {
			case TYPE_MESSAGE: return new LineMessage(result);
			case TYPE_ACTION: return new LineAction(result);
			case TYPE_ENTERLEAVE: return new LineEnterLeave(result);
			case TYPE_KICK: return new LineKick(result);
			case TYPE_MODE: return new LineMode(result);
			default: return new LineOther(result);
		}
	}
	
	private long getOldestTime(String channel) throws SQLException {
		QuerySelect q = new QuerySelect(SQL.getTable("rollback"));
		if (channel != null)
			q.addCriterions(new CriterionString("channel",channel.toLowerCase()));
		q.setLimitCount(1);
		ResultSet j = SQL.select(q);
		try {
			if (j == null || !j.next())
				throw new SQLException("Cannot find oldest timestamp for "+channel);
			return j.getLong("stamp");
		} finally {
			if (j != null)
				j.close();
		}
	}
//	private <T extends Line> ResultSet getResults(Class<T> type, String channel, String user, String regex, String cull, boolean newest, int lines, int seconds) throws SQLException {
//		ResultSet ret = getResults(type, channel, user, regex, cull, newest, lines, seconds, true).rs;
//		return ret;
//	}

    @Deprecated
    private <T extends Line> ConnStatResultSet getResults(
            Class<T> type,
            String channel,
            String user,
            String regex,
            String cull,
            boolean newest,
            int lines,
            int seconds,
            boolean close
    ) throws SQLException {
	    return getResults(type, channel, user, regex, cull, newest, lines, seconds, -1, close);
    }

	private <T extends Line> ConnStatResultSet getResults(
	        Class<T> type,
            String channel,
            String user,
            String regex,
            String cull,
            boolean newest,
            int lines,
            int seconds,
            int at,
            boolean close
    ) throws SQLException {
		int intType = TYPE_OTHER;
		if (type == LineMessage.class)
		    intType = TYPE_MESSAGE;
		else if (type == LineAction.class)
		    intType = TYPE_ACTION;
		else if (type == LineEnterLeave.class)
		    intType = TYPE_ENTERLEAVE;
		else if (type == LineKick.class)
		    intType = TYPE_KICK;
		else if (type == LineMode.class)
		    intType = TYPE_MODE;
		else if (type == LineWithUsers.class)
		    intType = TYPE_MESSAGEACTION;
		
		QuerySelect q = new QuerySelect(SQL.getTable("rollback"));

		if (channel != null)
		    q.addCriterions(new CriterionString("channel",channel.toLowerCase()));

		if (user != null)
		    q.addCriterions(new CriterionString("';' || users || ';'",Operation.LIKE,"%;"+user.toLowerCase()+";%"));

		if (lines != 0)
			q.setLimitCount(Math.min(lines, 3000));
		else
			q.setLimitCount(3000);


		if (seconds != 0) {
			Operation op;
			long base;
			int mult;

			long milliseconds = (seconds * 1000);

			if (at != -1) {
			    base = ((long) at) * 1000;
			    mult = (newest ? 1 : -1);
			    op = (newest ? Operation.LesserOrEqual : Operation.GreaterOrEqual);
            } else {
			    if (newest) {
                    base = System.currentTimeMillis();
                    op = Operation.GreaterOrEqual;
                    mult = -1;
                } else {
                    base = getOldestTime(channel);
                    op = Operation.LesserOrEqual;
                    mult = 1;
                }
            }
            q.addCriterions(new CriterionNumber("stamp", op, base + (mult * milliseconds)));
		}
		if (at != -1) {
            q.addCriterions(
                    new CriterionNumber("stamp",
                            newest ? Operation.GreaterOrEqual : Operation.LesserOrEqual,
                            ((long) at) * 1000)
            );
        } else {
		    // To give us a correct order!
            newest = !newest;
		}


		if (regex != null && !regex.isEmpty())
			q.addCriterions(new CriterionString("text",Operation.REGEXP,regex));
		if (cull != null && !cull.isEmpty())
			q.addCriterions(new CriterionString("text",cull,false));
		if (type != Line.class) {
			if (intType != TYPE_MESSAGEACTION)
				q.addCriterions(new CriterionNumber("type",Operation.Equals,intType));
			else {
				CriterionNumber cn1 = new CriterionNumber("type",Operation.Equals,TYPE_MESSAGE);
				CriterionNumber cn2 = new CriterionNumber("type",Operation.Equals,TYPE_ACTION);
                cn1.setOR();
                q.addCriterions(cn1, cn2);
            }
		}

		q.addOrder("stamp", newest);
			
		return SQL.select(q, close);
	}

	@SuppressWarnings("unchecked")
	public synchronized <T extends Line> ArrayList<T> getRollbackLines(
	        Class<T> type,
            String channel,
            String user,
            String regex,
            String cull,
            boolean newest,
            int lines,
            int seconds,
            int at) {
		ArrayList<T> ret = new ArrayList<T>();
		ConnStatResultSet csrs = null;
		ResultSet result = null;
		try {
			csrs = getResults(type, channel, user, regex, cull, newest, lines, seconds, at, false);
			result = csrs.rs;
			if (result != null) {
				while(result.next())
					ret.add((T)getLine(result));

				if (at != -1)
				    newest = !newest;
				if (newest)
				    Collections.reverse(ret);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
				try {
					if (result != null && !result.isClosed())
						result.close();
					csrs.c.close();
				} catch (SQLException ignored) {
				}
		}
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	public synchronized <T extends Line> T getRollbackLine(
	        ILinePredicate<T> predicate,
            Class<T> type,
            String channel,
            String user,
            String regex,
            String cull,
            boolean newest,
            int lines,
            int seconds,
            int at) {
		ConnStatResultSet csrs = null;
		ResultSet result = null;
		try {
			csrs = getResults(type, channel, user, regex, cull, newest, lines, seconds, false);
			result = csrs.rs;
			if (result != null) {
				while(result.next()) {
					T line = (T) getLine(result);
					if (predicate.accepts(line))
						return line;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (result != null && !result.isClosed())
					result.close();
				csrs.c.close();
			} catch (Exception ignored) {
			}
		}
		return null;
	}
	
	public class CmdPastebin extends Command {
		public String command() {return "pastebin";}
		public String help(Parameters params) {
			StringBuilder sb = new StringBuilder();
			sb.append("pastebin/pb\n");
            sb.append("pastebin [channel] [user] {lines} - uploads last lines to paste.kde.org/pastebin.com/pastebin.ca\n");
            sb.append("pastebin [channel] [user] -{lines} - uploads first lines to paste.kde.org/pastebin.com/pastebin.ca\n");
            sb.append("pastebin [channel] [user] {time}{d/h/m/s} - uploads last lines from set time to paste.kde.org/pastebin.com/pastebin.ca\n");

			
			return sb.toString();
		}
		
		public void doCommand(Parameters params, CommandCallback callback) {
			String[] args = params.input.split(" ");
			String pbLink = null, regex = null;
			callback.type = EType.Notice;
			
			if (args.length > 0) {
				for (int i = args.length-1; i > 0; i--) {
					if (args[i].equals("|")) {
						regex = StringTools.implode(params.input,i+1," ");
						args = StringTools.implode(args,0,i-1," ").split(" ");
						break;
					}
				}
			}
			
			if (args.length < 1) {
				callback.append(help(params));
				return;
			}

            String aChannel = null, aUser = null;
			int lines = 0, time = 0, at = -1, i = 0;

			boolean newest = true;
			boolean parseAt = false;

            for (; i < args.length; i++) {
                String arg = args[i];
                if (!arg.startsWith("#")) {
                    Matcher m = durationPattern.matcher(arg);
                    if (!m.find()) {
                        if (!arg.toLowerCase().startsWith("at") &&
                                !arg.startsWith("@") &&
                                !arg.startsWith("after") &&
                                !arg.startsWith("before")) {
                            if (aUser != null) {
                                callback.append("Cannot have multiple users arguments in query!\n");
                                callback.append(help(params));
                                return;
                            }
                            aUser = arg;
                        } else {
                            if (time == 0 && lines == 0 && aUser == null) {
                                callback.append("No time/lines/user set in query! Using default of one day.\n");
                                time = 86400;
                            }
                            parseAt = true;
                            break;
                        }
                    } else {
                        if (time != 0 || lines != 0) {
                            callback.append("Cannot have multiple time/lines arguments in query!\n");
                            callback.append(help(params));
                            return;
                        }

                        newest = arg.charAt(0) != '-';
                        do {
                            int parseInt = Integer.parseInt(m.group(1));
                            char c = m.group(2).toLowerCase().charAt(0);
                            switch (c) {
                                case 'l': lines+= parseInt; break;
                                case 's': time += parseInt; break;
                                case 'm': time += parseInt*60; break;
                                case 'h': time += parseInt*3600; break;
                                case 'd': time += parseInt*86400; break;
                            }
                        } while (m.find());
                    }
                } else {
                    if (aChannel != null) {
                        callback.append("Cannot have multiple channels in query!\n");
                        callback.append(help(params));
                        return;
                    }
                    aChannel = arg;
                }
            }

            if (parseAt) {
                StringBuilder sb = new StringBuilder(args[i++]);
                for (; i < args.length; i++) {
                    sb.append(" ").append(args[i]);
                }
                String atFull = sb.toString();
                if (atFull.toLowerCase().startsWith("at")) {
                    atFull = atFull.substring(2);
                } else if (atFull.startsWith("@")) {
                    atFull = atFull.substring(1);
                } else if (atFull.startsWith("after")) {
                    atFull = atFull.substring(5);
                } else if (atFull.startsWith("before")) {
                    atFull = atFull.substring(6);
                    newest = !newest;
                }
                atFull = atFull.trim();

                Matcher m = durationPattern.matcher(atFull);
                if (m.find()) {
                    at = (int) (System.currentTimeMillis() / 1000);
                    do {
                        int parseInt = Integer.parseInt(m.group(1));
                        char c = m.group(2).toLowerCase().charAt(0);
                        switch (c) {
                            case 's': at -= parseInt; break;
                            case 'm': at -= parseInt*60; break;
                            case 'h': at -= parseInt*3600; break;
                            case 'd': at -= parseInt*86400; break;
                        }
                    } while (m.find());
                } else {
                    String format = determineDateFormat(atFull);
                    if (format == null) {
                        try {
                            at = Integer.parseInt(atFull);
                        }
                        catch (NumberFormatException e) {
                            callback.append("Unknown time format for \"at\" in query!\n");
                            callback.append(help(params));
                            return;
                        }
                    } else {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
                        try {
                            at = (int) (simpleDateFormat.parse(atFull).getTime() / 1000);
                        } catch (ParseException e) {
                            System.out.printf("\"%s\" format: \"%s\"", atFull, format);
                            callback.append("Unknown error while parsing \"at\" in query!\n");
                            callback.append(help(params));

                            e.printStackTrace();
                            return;
                        }
                    }

                }
            }

            if (aChannel == null && aUser == null) {
                if (params.type == EType.Channel) aChannel = params.channel.getName(); else {
                    callback.append(help(params));
                    return;
                }
            }
            if (aChannel != null) aChannel = aChannel.toLowerCase();
            if (time == 0 && lines == 0 && aUser != null) lines = 100;

            ArrayList<Line> list;
            list = getRollbackLines(aChannel, aUser, regex, null, newest, Math.abs(lines), time, at);

            if (list.isEmpty()) {
                callback.append("Nothing to upload");
                return;
            }
            pbLink = getLink(list,aUser != null && aChannel == null);

            if (pbLink != null) {
				callback.type = EType.Channel;
				callback.append(pbLink);
			}
		}
		
		public String getLink(ArrayList<Line> lines, boolean withChannel) {
			IWebServer ws = null;
			try {
				ws = (IWebServer) Module.getModule("webserver");
			} catch (Exception e) {
				e.printStackTrace();
			}
			StringBuilder sb = new StringBuilder();
			Line.setWithChannels(withChannel);
			if (ws!=null)
				appendLines(sb, lines, !ws.exists());
			else
				appendLines(sb, lines, false);
			Line.setWithChannels(false);
			

			String link = null;
			try {
				IPaste p = (IPaste) Module.getModule("paste");
				if (!(p==null)){
					link = p.paste(sb);
				} else {
					return "Module 'paste' is not loaded";
				}
			} catch (Exception e){
				e.printStackTrace();
			}
			if (link != null)
				return link;
			return "Failed with all services";
		}
	}
}
