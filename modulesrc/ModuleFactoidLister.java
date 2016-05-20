import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import pl.shockah.shocky.Module;
import pl.shockah.shocky.cmds.Command;
import pl.shockah.shocky.cmds.CommandCallback;
import pl.shockah.shocky.cmds.Parameters;
import pl.shockah.shocky.interfaces.IFactoid;
import pl.shockah.shocky.interfaces.IWebServer;
import pl.shockah.shocky.sql.Factoid;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class ModuleFactoidLister extends Module {
    protected Command cmd;
    private ListerHandler handler = new ListerHandler();
    private String url;

    @Override
    public String name() {
        return "factoidlister";
    }

    public void onEnable(File f) {
        Command.addCommands(this, cmd = new CmdLister());
        IWebServer ws = null;
        try {
            ws = (IWebServer) Module.getModule("webserver");
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (ws != null && ws.exists()) {
            ws.createContext("/m/factoids", handler);
            url = ws.getURL("/m/factoids");
        }
    }

    public class CmdLister extends Command {
        public String command() {return "flister";}
        public String help(Parameters params) {
            StringBuilder sb = new StringBuilder();
            sb.append("flister/g");
            sb.append("\nflister [cmd] - returns the list of all factoids or manages it");
            return sb.toString();
        }
        @Override
        public void doCommand(Parameters params, CommandCallback callback) {
            if (params.tokenCount==0) {
                callback.type = EType.Channel;
                callback.append(url);
                return;
            }
            String param = params.nextParam();
            if (param.equals("reload")) {
                handler.contentCache = null;
                try {
                    handler.contentCache = handler.getFactoids();
                    callback.type = EType.Notice;
                    callback.append("Done!");
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                callback.type = EType.Notice;
                callback.append("Failed!");
            }
        }
    }

    private class ListerHandler implements HttpHandler {

        String timeFormat = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat dateFormat = new SimpleDateFormat(timeFormat);
        String contentCache;
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            Headers headers = httpExchange.getResponseHeaders();
            headers.add("Content-Type", "text/plain; charset=utf-8");
            headers.add("Cache-Control", "private; max-age=90");

            byte[] buffer;

            if (contentCache == null) {
                buffer = "No factoids data available yet.".getBytes(StandardCharsets.UTF_8);
                httpExchange.sendResponseHeaders(404, buffer.length);
                try (OutputStream os = httpExchange.getResponseBody()) {
                    os.write(buffer, 0, buffer.length);
                }
                return;
            }

            httpExchange.sendResponseHeaders(200, contentCache.length());
            try (OutputStream os = httpExchange.getResponseBody();
                 InputStream is = new ByteArrayInputStream(contentCache.getBytes(StandardCharsets.UTF_8))) {
                buffer = new byte[1024];
                int count;
                while ((count = is.read(buffer, 0, buffer.length)) > 0)
                    os.write(buffer, 0, count);
            }
        }

        private Integer getMaxLength(Factoid[] factoids, Field field) {
            System.out.println("Getting a length of field " + field.getName());
            int max = -1;
            for (Factoid factoid : factoids) {
                try {
                    String str = (String) field.get(factoid);
                    if (str == null)
                        continue;
                    if (str.length() > max)
                        max = str.length();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            return max;
        }


        private String padForColumn(String s, int padding) {
            return s + "     ";//String.format("%1$-" + (padding - s.length()) + "s", s);
        }

        private String getFactoid(Factoid factoid) {
            String sb = padForColumn(factoid.name, lengths.get("name") + 5) +
                    padForColumn(factoid.author, lengths.get("author") + 5) +
                    padForColumn(dateFormat.format(new Date(factoid.stamp)), timeFormat.length() + 5) +
                    padForColumn(factoid.forgotten ? "Yes" : "No", 9 + 5) +
                    padForColumn(factoid.rawtext, lengths.get("rawtext"));
            return sb;
        }

        private String getFactoids() {
            StringBuilder sb = new StringBuilder();
            IFactoid f;
            try {
                f = (IFactoid) Module.getModule("factoid");
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            if (f != null) {
                Factoid[] factoids = f.getFactoids();
                HashMap<String, Integer> lengths = new HashMap<>();

                for (Field field : Factoid.class.getFields()) {
                    if (field.getType().getSimpleName().equals("String")) {
                        lengths.put(field.getName(), getMaxLength(factoids, field));
                    }
                }

                sb.append(padForColumn("Name", lengths.get("name") + 5))
                  .append(padForColumn("Author", lengths.get("author") + 5))
                  .append(padForColumn("Time", timeFormat.length() + 5))
                  .append(padForColumn("Forgotten", 9+5))
                  .append(padForColumn("Raw Source", lengths.get("rawtext")))
                  .append("\n");

                for (Factoid factoid : factoids) {
                    sb.append(getFactoid(factoid, lengths)).append("\n");
                }

            } else {
                sb.append("Unable to connect to database.");
            }

            return sb.toString();
        }
    }
}
