import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;

import pl.shockah.HTTPQuery;
import pl.shockah.StringTools;
import pl.shockah.shocky.Data;
import pl.shockah.shocky.Module;
import pl.shockah.shocky.cmds.Command;
import pl.shockah.shocky.cmds.CommandCallback;
import pl.shockah.shocky.cmds.Parameters;
import pl.shockah.shocky.cmds.Command.EType;
import pl.shockah.shocky.interfaces.ILua;

public class ModuleGoogle extends Module implements ILua {
	protected Command cmd1;
	protected Command cmd2;

	@Override
	public String name() {return "google";}
	@Override
	public void onEnable(File dir) {
		Command.addCommands(this, cmd1 = new CmdGoogle(), cmd2 = new CmdGoogleImg());
		Command.addCommand(this, "g", cmd1);
	}
	
	@Override
	public void onDisable() {
		Command.removeCommands(cmd1,cmd2);
	}

    public JSONObject getJSON(boolean images, String search) throws IOException, JSONException {
        return getJSON(images, search, 1);
    }

	public JSONObject getJSON(boolean images, String search, int num) throws IOException, JSONException {
        StringBuilder requestURL = new StringBuilder("https://www.googleapis.com/customsearch/v1?");
        if (images) requestURL.append("searchType=image&");
        requestURL.append("safe=off&")
                .append("key=").append(URLEncoder.encode(Data.config.getString("google-key"), "UTF8")).append("&")
                .append("cx=").append(URLEncoder.encode(Data.config.getString("custom-search"), "UTF8")).append("&")
                .append("num=").append(num).append("&")
                .append("q=")
                .append(URLEncoder.encode(search, "UTF8"));

		HTTPQuery q = HTTPQuery.create(requestURL.toString());
		try {
			q.connect(true, false);
            return new JSONObject(q.readWhole());
		} finally {
			q.close();
		}
	}
	
	public void doSearch(Command cmd, Parameters params, CommandCallback callback) {
		if (params.tokenCount == 0) {
			callback.type = EType.Notice;
			callback.append(cmd.help(params));
			return;
		}
		
		try {
			JSONObject results = getJSON(cmd instanceof CmdGoogleImg, params.input);
			if (results.getJSONObject("searchInformation").getString("totalResults").equals("0")) {
				callback.append("No results.");
				return;
			}

			JSONObject r = results.getJSONArray("items").getJSONObject(0);
			String title = StringTools.ircFormatted(r.getString("title"), false);
			String url = StringTools.ircFormatted(r.getString("link"), false);
			callback.append(url);
			callback.append(" -- ");
			callback.append(title);

            if (r.has("image") && r.getJSONObject("image").has("contextLink"))
                callback.append(" (").append(StringTools.ircFormatted(r.getJSONObject("image").getString("contextLink"), false)).append(")");
			callback.append(": ");

			if (r.has("snippet") && r.getString("snippet").length() > 0)
				callback.append(StringTools.ircFormatted(r.getString("snippet"), false));
			else
				callback.append("No description available.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public class CmdGoogle extends Command {
		public String command() {return "google";}
		public String help(Parameters params) {
			StringBuilder sb = new StringBuilder();
			sb.append("google/g");
			sb.append("\ngoogle {query} - returns the first Google search result");
			return sb.toString();
		}
		@Override
		public void doCommand(Parameters params, CommandCallback callback) {
			doSearch(this, params, callback);
		}
	}
	
	public class CmdGoogleImg extends Command {
		public String command() {return "gis";}
		public String help(Parameters params) {
			StringBuilder sb = new StringBuilder();
			sb.append("gis");
			sb.append("\ngis {query} - returns the first Google Image search result");
			return sb.toString();
		}
		@Override
		public void doCommand(Parameters params, CommandCallback callback) {
			doSearch(this, params, callback);
		}
	}
	
	public class Function extends OneArgFunction {
		private final boolean images;
		public Function(boolean images) {
			this.images = images;
		}
		
		@Override
		public LuaValue call(LuaValue arg) {
			try {
				JSONObject query = getJSON(images, arg.checkjstring(), 50);
				LuaValue[] values = new LuaValue[Math.min(
                        query.getJSONObject("queries").getJSONArray("request").getJSONObject(1).getInt("count"),
                        new Integer(query.getJSONObject("searchInformation").getString("totalResults")))
                        ];
                if (values.length == 0)
                    return NIL;

                JSONArray items = query.getJSONArray("items");
				for (int i = 0;i < values.length;++i)
					values[i] = getResultTable(items.getJSONObject(i));
				return listOf(values);
			} catch (JSONException | IOException e) {
				e.printStackTrace();
			}
            return NIL;
		}
		
		private LuaValue getResultTable(JSONObject result) throws JSONException {
			LuaTable t = new LuaTable();
			t.rawset("url", result.getString("link"));
			t.rawset("title", result.getString("title"));
            if (result.has("image") && result.getJSONObject("image").has("context"))
                t.rawset("context", result.getJSONObject("image").getString("context"));
            if (result.has("snippet"))
                t.rawset("desc", result.getString("snippet"));
			return t;
		}
	}

	@Override
	public void setupLua(LuaTable env) {
		env.rawset("gs", new Function(false));
		env.rawset("gis", new Function(true));
	}
}