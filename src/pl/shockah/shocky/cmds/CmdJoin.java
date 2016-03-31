package pl.shockah.shocky.cmds;

import pl.shockah.shocky.MultiChannel;

public class CmdJoin extends Command {
	public String command() {return "join";}
	public String help(Parameters params) {
		return "join {channel} - makes the bot join channel";
	}
	
	public void doCommand(Parameters params, CommandCallback callback) {
		params.checkController();
		callback.type = EType.Notice;
		if (params.tokenCount == 1) {
			String channel = params.nextParam();
			try {
				MultiChannel.join(params.bot.getID(), channel);
			} catch (Exception e) {
				e.printStackTrace();
				callback.append("Already in channel ").append(channel);
			}
			return;
		}
		
		callback.append(help(params));
	}
}