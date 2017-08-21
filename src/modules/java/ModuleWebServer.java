import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.UUID;

import pl.shockah.Helper;
import pl.shockah.shocky.Data;
import pl.shockah.shocky.Module;
import pl.shockah.shocky.cmds.Command;
import pl.shockah.shocky.cmds.CommandCallback;
import pl.shockah.shocky.cmds.Parameters;
import pl.shockah.shocky.interfaces.IWebServer;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


public class ModuleWebServer extends Module implements IWebServer {
    private static HashMap<String,HttpContext> contexts = new HashMap<>();
    private static HttpServer server;
    private Command cmd;
    private static final String mname = "webserver";
    private static final String hostname = "nikky.moe";
    private String baseURL = "http://nikky.moe:8000";
	
	@Override
	public String name() {
		return mname;
	}
	
	public void onEnable(File f) {
		System.out.printf("[Module %s] Starting up...", mname).println();
		try {
			if (!this.start(hostname, 8000))
				System.out.printf("[Module %s] WebSever failed to start.\n", mname);;
		} catch (IOException e) {
			e.printStackTrace();
		}
		Command.addCommands(this, cmd = new CmdWeb());
	}
	
	public void onDisable() {
		Command.removeCommands(cmd);
		this.stop();
	}
	
	public boolean start(String host, int port) throws IOException {
		Data.config.setNotExists("web-hostname","");
		try {
			InetSocketAddress addr = new InetSocketAddress(Inet4Address.getByName(hostname), port);
			if (exists())
				server.stop(0);
			server = HttpServer.create(addr, 0);
			server.start();
			Class.forName("ModuleWebServer$RedirectHandler");
			Class.forName("ModuleWebServer$PasteHandler");
			System.out.printf("[Module %s] WebServer has started.", mname).println();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public void stop() {
        if (!exists())
			return;
		server.stop(0);
		server = null;
		System.out.printf("[Module %s] WebServer has been stopped.", mname).println();
	}
	
	public boolean exists() {
		return (server != null);
	}

    public InetSocketAddress address() {
		if (server == null)
			return null;
		return server.getAddress();
	}

	public String getURL() {
		return getURL("");
	}

	public String getURL(String path) {
        if (baseURL != null)
            return baseURL + path;
		if (!exists())
			return null;
		String l_hostname = Data.config.getString("web-hostname");
		if (!l_hostname.isEmpty())
			return l_hostname + path;
		InetSocketAddress addr = address();
		StringBuilder sb = new StringBuilder("http://");
		sb.append(hostname);
		if (addr.getPort()!=80)
			sb.append(':').append(addr.getPort()).append(path);
		return sb.toString();
	}

    public HttpContext createContext(String url, HttpHandler handler) {
        HttpContext context = server.createContext(url, handler);
        contexts.put(url, context);
        return context;
    }

    public boolean removeContext(String contextURL) {
        return removeContext(contexts.get(contextURL));
    }

	public boolean removeContext(HttpContext context) {
		if (server == null)
			return false;
		server.removeContext(context);
        contexts.remove(context);
		return true;
	}
	
	public HttpContext addRedirect(String url) {
		if (server == null)
			return null;
		UUID id = UUID.randomUUID();
		return createContext("/s/" + id.toString(), new RedirectHandler(url));
	}
	
	public HttpContext addPaste(File file) {
		if (server == null)
			return null;
		UUID id = UUID.randomUUID();
		return createContext("/s/" + id.toString(), new PasteHandler(file));
	}
	
	private class RedirectHandler implements HttpHandler {
		public final String url;

		public RedirectHandler(String url) {
			this.url = url;
		}

		@Override
		public void handle(HttpExchange httpExchange) throws IOException {
			Headers headers = httpExchange.getResponseHeaders();
			headers.add("Content-Type", "text/plain; charset=utf-8");
			headers.add("Cache-Control", "private; max-age=90");
			headers.add("Location", url);
            byte[] out = url.getBytes(Helper.utf8);
            httpExchange.sendResponseHeaders(301, out.length);
            OutputStream os = httpExchange.getResponseBody();
            os.write(out);
            os.close();
		}
	}
	
	private class PasteHandler implements HttpHandler {
		public final File file;

		public PasteHandler(File file) {
			this.file = file;
		}

		@Override
		public void handle(HttpExchange httpExchange) throws IOException {
			Headers headers = httpExchange.getResponseHeaders();
			headers.add("Content-Type", "text/plain; charset=utf-8");
			headers.add("Cache-Control", "private; max-age=90");
			
			byte[] buffer;
			if (!(file.exists() && file.canRead())) {
				buffer = "Paste not found.".getBytes(Helper.utf8);
				httpExchange.sendResponseHeaders(404, buffer.length);
                try (OutputStream os = httpExchange.getResponseBody()) {
                    os.write(buffer, 0, buffer.length);
                }
				return;
			}
			httpExchange.sendResponseHeaders(200, file.length());

            try (OutputStream os = httpExchange.getResponseBody(); InputStream is = new FileInputStream(file)) {
                buffer = new byte[1024];
                int count;
                while ((count = is.read(buffer, 0, buffer.length)) > 0)
                    os.write(buffer, 0, count);
            }
		}
	}
	
	public class CmdWeb extends Command {
		public String command() {return "web";}
		public String help(Parameters params) {
			return "[r:controller] web {cmd} [args] - configures the web server";
		}
		
		public void doCommand(Parameters params, CommandCallback callback) {
			params.checkController();
			callback.type = EType.Notice;
			if (params.tokenCount == 0) {
				callback.append(help(params));
				return;
			}
			
			String command = params.nextParam();
			if (command.equalsIgnoreCase("stop")) {
				stop();
				callback.append("Done.");
				return;
			}
			
			if (command.equalsIgnoreCase("start")) {
				String host;
				int port;
				
				if (params.hasMoreParams())
					host = params.nextParam();
				else
					host = params.bot.getUserBot().getHostmask();
				
				try {
					if (params.hasMoreParams())
						port = Integer.valueOf(params.nextParam());
					else
						port = 8000;
				
					start(host, port);
					callback.append("Done.");
				} catch (IOException | NumberFormatException e) {
					callback.append(e.getLocalizedMessage());
				}
				return;
			}

            if (command.equalsIgnoreCase("url")) {
                if (params.hasMoreParams())
                    baseURL = params.nextParam();
                else
                    baseURL = null;
            }
			
			callback.append(help(params));
		}
	}
}
