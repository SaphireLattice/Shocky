import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.pircbotx.Colors;
import org.pircbotx.DccChat;
import org.pircbotx.DccManager;
import org.pircbotx.PircBotX;
import org.pircbotx.ShockyBot;
import org.pircbotx.User;
import org.pircbotx.hooks.events.IncomingChatRequestEvent;

import pl.shockah.shocky.Data;
import pl.shockah.shocky.Module;
import pl.shockah.shocky.MultiChannel;
import pl.shockah.shocky.Shocky;
import pl.shockah.shocky.interfaces.ILogger;
import pl.shockah.shocky.sql.SQL;


public class ModuleDCC extends Module {
	
	public static interface ProgramFactory {
		Program start(DccChat chat);
		Program start(DccChat chat, PircBotX bot);
	}
	
	public Map<String,ProgramFactory> programs = new HashMap<String,ProgramFactory>();
	private List<Session> sessions = new LinkedList<Session>();
	
	@Override
	public boolean isListener() {return true;}
	@Override
	public String name() {return "dcc";}
	
	@Override
	public void onEnable(File dir) {
		programs.put("logger", new LoggerFactory());
		programs.put("sql", new SQLFactory());
		programs.put("cat", new CatFactory());
	}
	@Override
	public void onDisable() {
		synchronized(sessions) {
			if (!sessions.isEmpty()) {
				Iterator<Session> iter = sessions.iterator();
				while(iter.hasNext()) {
					DccChat chat = iter.next().chat;
					try {chat.close();} catch (IOException e) {}
					iter.remove();
				}
			}
		}
		programs.clear();
	}
	
	public static boolean isController(PircBotX bot, User user) {
		if (bot.getInetAddress().isLoopbackAddress()) return true;
		if (Shocky.getLogin(user) == null) return false;
		return Data.controllers.contains(Shocky.getLogin(user));
	}

	@Override
	public void onIncomingChatRequest(IncomingChatRequestEvent<ShockyBot> event)
			throws Exception {
		DccChat chat = event.getChat();
		if (!isController(event.getBot(),chat.getUser()))
			return;
		Session session = new Session(event.getBot().getDccManager(),chat,event.getBot());
		new Thread(session,"DCC - "+chat.getUser().getNick()).start();
	}
	
	public class Session extends Program {
		private final PircBotX bot;
		private final DccManager manager;
		
		public Session(DccManager manager, DccChat chat, PircBotX bot) {
			super(chat);
			this.manager = manager;
			this.bot = bot;
		}
		
		@Override
		public String name() {return "dcc session";}

		@Override
		public void run() {
			try {
				chat.accept();
				synchronized(sessions) {
					sessions.add(this);
				}
				super.run();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (manager.getAllChats().contains(chat))
						chat.close();
				} catch (IOException e) {
				}
				synchronized(sessions) {
					sessions.remove(this);
				}
			}
		}
		
		@Override
		public boolean handleLine(String line) throws IOException {
			StringTokenizer tokenizer = new StringTokenizer(line);
			String cmd = tokenizer.nextToken();
			if (cmd.contentEquals("run") && tokenizer.hasMoreTokens()) {
				String run = tokenizer.nextToken();
				if (programs.containsKey(run)) {
					ProgramFactory factory = programs.get(run);
					Program program = factory.start(chat,bot);
					program.onStart();
					program.run();
				}
			} else if (cmd.contentEquals("help")) {
				chat.sendLine("Commands available: run, help, connect, disconnect");
				StringBuilder sb = new StringBuilder("Programs available: ");
				List<String> names = new LinkedList<String>(programs.keySet());
				Collections.sort(names);
				Iterator<String> iter = names.iterator();
				int i = 0;
				while(iter.hasNext()) {
					if (i++ > 0)
						sb.append(", ");
					sb.append(iter.next());
				}
				chat.sendLine(sb.toString());
			} else if (cmd.contentEquals("connect")) {
				if (!bot.isConnected()) {
					try {
						System.out.print("Connecting ");
						System.out.println(bot.getName());
						chat.sendLine("Connecting ".concat(bot.getName()));
						Thread.sleep(10000);
						
						if (MultiChannel.connect(bot))
							MultiChannel.join(Collections.singletonList(bot),Data.channels.toArray(new String[0]));
					} catch (Exception e) {
						e.printStackTrace();
					}
					chat.sendLine("Connected!");
				}
				else {
					chat.sendLine("Already connected! Use `disconnect` first");
				}
			} else if (cmd.contentEquals("disconnect")) {
				if (bot.isConnected()) {
					bot.disconnect();
					chat.sendLine("Disconnected!");
				}
				else {
					chat.sendLine("Already disconnected!");
				}
			}
			return true;
		}
	}
	
	public static abstract class Program implements Runnable {
		public final DccChat chat;
		public Program(DccChat chat) {
			this.chat = chat;
		}
		public abstract String name();
		public abstract boolean handleLine(String line) throws IOException;
		public void onStart() throws IOException {
			chat.sendLine("\1ACTION is starting "+name()+'\1');
		}
		public void onStop() throws IOException {
			chat.sendLine("\1ACTION is stopping "+name()+'\1');
		}
		@Override
		public void run() {
			try {
				String line;
				while((line = chat.readLine()) != null) {
					if (line.contentEquals("exit"))
						break;
					if (line.contentEquals("quit"))
						break;
					if (!handleLine(line))
						break;
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {onStop();} catch (IOException e) {}
			}
		}
	}
	
	public static class LoggerFactory implements ProgramFactory {
		@Override
		public Logger start(DccChat chat,PircBotX bot) {
			return new Logger(chat,bot);
		}

		@Override
		public Program start(DccChat chat) {
			return null;
		}
	}
	
	public static class Logger extends Program implements ILogger {
		private PircBotX bot;
		public String name() {return "logger";}
		public Logger(DccChat chat,PircBotX bot) {
			super(chat);
			this.bot = bot;
		}
		
		@Override
		public void onStart() throws IOException {
			super.onStart();
			ShockyBot.addLogger(this);
		}
		
		@Override
		public void onStop() throws IOException {
			ShockyBot.removeLogger(this);
			super.onStop();
		}
		
		@Override
		public boolean handleLine(String line) throws IOException {
			StringBuilder sb = new StringBuilder();
			StringTokenizer tokenizer = new StringTokenizer(line);
			String cmd = tokenizer.nextToken();
			
			if (cmd.contentEquals("say") && tokenizer.hasMoreTokens()) {
				String target = tokenizer.nextToken();
				sb.append("PRIVMSG ");
				sb.append(target);
				sb.append(" :");
				while (tokenizer.hasMoreTokens()) {
					sb.append(tokenizer.nextToken());
					sb.append(" ");
				}
				bot.sendRawLine(sb.toString());
			} else if (cmd.contentEquals("action") && tokenizer.hasMoreTokens()) {
				String target = tokenizer.nextToken();sb.append("PRIVMSG ");
				sb.append(target);
				sb.append(" :\1ACTION ");
				
				while (tokenizer.hasMoreTokens()) {
					sb.append(tokenizer.nextToken());
					sb.append(" ");
				}
				sb.append("\1");
				bot.sendRawLine(sb.toString());
			} else if (cmd.contentEquals("help")) {
				chat.sendLine("Commands available: quit, say, action");
			}
			return true;
		}

		@Override
		public void log(String line) throws IOException {
			chat.sendLine(line);
		}
	}
	
	public static class SQLFactory implements ProgramFactory {
		@Override
		public SQLConsole start(DccChat chat) {
			return new SQLConsole(chat);
		}

		@Override
		public Program start(DccChat chat, PircBotX bot) {
			return new SQLConsole(chat);
		}
	}
	
	public static class SQLConsole extends Program {
		private Connection connection;
		
		public String name() {return "sql";}
		public SQLConsole(DccChat chat) {
			super(chat);
			connection = SQL.getSQLConnection();
		}
		
		@Override
		public boolean handleLine(String line) throws IOException {
			try {
				handleSQL(line);
			} catch (SQLException e) {
				chat.sendLine(e.getMessage());
			}
			return true;
		}
		
		private void handleSQL(String line) throws SQLException, IOException {
			PreparedStatement statement = null;
			ResultSet set = null;
			try {
				statement = connection.prepareStatement(line);
				if (!statement.execute())
				{
					chat.sendLine("Total rows affected: "+statement.getUpdateCount());
					return;
				}
				ResultSetMetaData metadata = statement.getMetaData();
				int columns = metadata.getColumnCount();
				StringBuilder sb = new StringBuilder();
				sb.append(Colors.UNDERLINE);
				for (int i = 1; i <= columns; ++i) {
					if (i!=1)
						sb.append('|');
					sb.append(metadata.getColumnName(i));
				}
				sb.append(Colors.UNDERLINE);
				chat.sendLine(sb.toString());
			
				set = statement.getResultSet();
				while(set.next()) {
					sb = new StringBuilder();
					for (int i = 1; i <= columns; ++i) {
						if (i!=1)
							sb.append('|');
						sb.append(set.getObject(i));
					}
					chat.sendLine(sb.toString());
				}
			} finally {
				if (set != null && !set.isClosed())
					set.close();
				if (statement != null && !statement.isClosed())
					statement.close();
			}
		}
	}
	
	public static class CatFactory implements ProgramFactory {
		@Override
		public CatProgram start(DccChat chat,PircBotX bot) {
			return new CatProgram(chat);
		}

		@Override
		public CatProgram start(DccChat chat) {
			return new CatProgram(chat);
		}
	}
	
	public static class CatProgram extends Program {
		public CatProgram(DccChat chat) {
			super(chat);
		}

		@Override
		public String name() {
			return "cat";
		}

		@Override
		public boolean handleLine(String line) throws IOException {
			if (line.toLowerCase().startsWith("ls")) {
				try {
					StringTokenizer tokenizer = new StringTokenizer(line);
					tokenizer.nextToken();
					Path path = Paths.get(tokenizer.nextToken());
					try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
						int i = 0;
						StringBuilder sb = new StringBuilder();
					    for (Path file : stream) {
					    	i++;
					    	if (i>5) {i=0; sb.append("\n");}
					    	sb.append(file.getFileName());
					    	sb.append("  ");
					    }
					    chat.sendLine(sb.toString());
					}
				} catch(Exception e) {
					chat.sendLine(e.getMessage());
				}
			    return true;
			} else {
			try {
				BufferedReader br = new BufferedReader(new FileReader(line));
				try {
					String bline = null;
					while ((bline = br.readLine()) != null) {
						chat.sendLine(bline);
					}
				} finally {
					br.close();
				}
				return true;
			} catch (Exception e) {
				chat.sendLine(e.getMessage());
			}
			return true;
			}
		}
	}
}
