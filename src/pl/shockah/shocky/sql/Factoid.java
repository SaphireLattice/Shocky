package pl.shockah.shocky.sql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Map;

import org.json.JSONObject;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;

import pl.shockah.shocky.interfaces.IFactoidRegistry;

import static javax.swing.UIManager.put;

public final class Factoid {
	public JSONObject toJSONObject() {
		try {
			JSONObject fjobject = new JSONObject();
			fjobject.put("id", this.id)
					.put("factoid", this.name)
					.put("channel", this.channel)
					.put("author", this.author)
					.put("rawtext", this.rawtext)
					.put("stamp", this.stamp / 1000)
					.put("locked", this.locked)
					.put("forgotten", this.forgotten);

			return fjobject;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Factoid fromJSONObject(JSONObject j) {
		try {
			return new Factoid(j.getLong("id"), j.getString("factoid"), j.getString("channel"), j
					.getString("author"), j.getString("rawtext"), j.getLong("stamp") * 1000, j
					.getBoolean("locked"), j.getBoolean("forgotten"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Factoid fromResultSet(ResultSet j) throws SQLException {
			if (!j.next())
				return null;
			return new Factoid(j.getLong("id"), j.getString("factoid"), j.getString("channel"), 
					j.getString("author"), j.getString("rawtext"), 
					j.getLong("stamp") * 1000, 
					j.getBoolean("locked"), 
					j.getBoolean("forgotten"));
	}

	public static Factoid[] arrayFromResultSet(ResultSet j) throws SQLException {
		LinkedList<Factoid> list = new LinkedList<Factoid>();
		Factoid f;
		while ((f = fromResultSet(j)) != null)
			list.add(f);
		return list.toArray(new Factoid[0]);
	}

	public long id;
	public final String name, channel, author, rawtext;
	public final long stamp;
	public final boolean locked, forgotten;
	public Map<String,Object> metadata = null;
	public Token[] tokens = null;
	public IFactoidRegistry registry = null;

	public Factoid(long id, String name, String channel, String author, String rawtext, long stamp) {
		this(id, name, channel, author, rawtext, stamp, false, false);
	}

	public Factoid(long id, String name, String channel, String author, String rawtext, long stamp, boolean locked, boolean forgotten) {
		this.id = id;
		this.name = name;
		this.channel = channel;
		this.author = author;
		this.rawtext = rawtext;
		this.stamp = stamp;
		this.locked = locked;
		this.forgotten = forgotten;
	}

	@Override
	public String toString() {
		return rawtext;
	}
	
	public static interface Token {
		CharSequence process(PircBotX bot, Channel channel, User sender, String message, String[] args) throws Exception;
	}
}