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
                handler.content = null;
                try {
                    handler.getFactoids();
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
        String content;

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            try {
                Headers headers = httpExchange.getResponseHeaders();
                headers.add("Content-Type", "text/plain; charset=utf-8");
                headers.add("Cache-Control", "private; max-age=90");

                byte[] buffer;

                if (content == null) {
                    buffer = "{\"error\": \"no factoids data available yet\"}".getBytes(StandardCharsets.UTF_8);
                    httpExchange.sendResponseHeaders(404, buffer.length);
                    try (OutputStream os = httpExchange.getResponseBody()) {
                        os.write(buffer, 0, buffer.length);
                    }
                    return;
                }

                httpExchange.sendResponseHeaders(200, 0);
                try (BufferedOutputStream os = new BufferedOutputStream(httpExchange.getResponseBody()); InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                    buffer = new byte[1024];
                    int count;
                    while ((count = is.read(buffer, 0, buffer.length)) > 0)
                        os.write(buffer, 0, count);
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

        private void getFactoids() {
            try {
                StringBuilder sb = new StringBuilder();
                IFactoid f = null;
                try {
                    f = (IFactoid) Module.getModule("factoid");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (f != null) {
                    if (content == null)
                        content = "[";
                    Factoid[] factoids = f.getFactoids();
                    for (int i = 0, factoidsLength = factoids.length; i < factoidsLength; i++) {
                        Factoid factoid = factoids[i];
                        content += factoid.toJSONObject().toString();
                        if (i != factoidsLength - 1)
                            content += ",";
                    }
                    content += "]";
                } else {
                    content = "{\"error\": \"unable to connect get factoids\"}";
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
