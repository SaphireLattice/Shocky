package pl.shockah.shocky.lines;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import org.pircbotx.ShockyBot;
import org.pircbotx.hooks.events.ActionEvent;
import pl.shockah.BinBuffer;
import pl.shockah.shocky.sql.QueryInsert;

public class LineAction extends LineWithUsers {
	public final String text;
	
	public LineAction(ResultSet result) throws SQLException {
		super(result,new String[]{result.getString("users")});
		this.text = result.getString("text");
	}
	
	public LineAction(String channel, String sender, String text) {this(new Date(),channel,sender,text);}
	public LineAction(long ms, String channel, String sender, String text) {this(new Date(ms),channel,sender,text);}
	public LineAction(ActionEvent<ShockyBot> event) {this(new Date(),event.getChannel().getName(),event.getUser().getNick(),event.getAction());}
	public LineAction(Date time, String channel, String sender, String text) {
		super(time,channel,new String[]{sender});
		this.text = text;
	}
	
	public LineAction(BinBuffer buffer) {
		super(buffer);
		this.text = buffer.readUString();
	}
	
	public void save(BinBuffer buffer) {
		super.save(buffer);
		buffer.writeUString(text);
	}

	public String getMessage() {
		return "* "+users[0]+" "+text;
	}
	@Override
	public void fillQuery(QueryInsert q) {
		super.fillQuery(q);
		q.add("text",text);
	}
	
	public int fillQuery(PreparedStatement p, int arg) throws SQLException {
		arg = super.fillQuery(p,arg);
		p.setString(arg++, text);
		return arg;
	}
}