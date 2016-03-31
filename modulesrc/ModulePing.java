import org.pircbotx.Channel;
import org.pircbotx.ShockyBot;
import org.pircbotx.User;
import org.pircbotx.hooks.events.NoticeEvent;
import org.pircbotx.hooks.events.PingEvent;
import org.pircbotx.hooks.types.GenericCTCPCommand;
import pl.shockah.shocky.Data;
import pl.shockah.shocky.Module;
import pl.shockah.shocky.Shocky;
import pl.shockah.shocky.cmds.Command;
import pl.shockah.shocky.cmds.CommandCallback;
import pl.shockah.shocky.cmds.Parameters;

import java.io.File;
import java.util.HashMap;


public class ModulePing extends Module {
    private Command cmdPing;
    private HashMap<Long,Ping> pings = new HashMap<>();

    public String name() {
        return "ping";
    }

    public void onEnable(File dir) {
        Command.addCommands(this, cmdPing = new CmdPing());
    }

    public boolean isListener() {
        return true;
    }

    public void onNotice(NoticeEvent<ShockyBot> event) throws Exception {
        System.out.println("[ModulePing] recieved a notice from "+event.getUser().getNick());
        String pingValueString = "";
        Long pingValue;
        try {
            System.out.println("[ModulePing] inside of try/catch");
            Long eventTimestamp = event.getTimestamp();
            pingValueString = event.getMessage().substring(String.valueOf(eventTimestamp).length()+7,event.getMessage().length()-1);
            pingValue = Long.parseLong(pingValueString);

            if (pings.containsKey(pingValue)); {
                System.out.println("[ModulePing] ping exists in 'HashMap pings'");
                Ping ping = pings.get(pingValue);
                if (!ping.bot.equals(event.getBot())) {
                    return;
                }
                System.out.println("[ModulePing] timestamps: " + ping.timestamp + "/" + eventTimestamp);
                StringBuilder sb = new StringBuilder("Ping to ");
                sb.append(ping.target)
                        .append(" ")
                        .append((eventTimestamp - ping.timestamp)/2)
                        .append("ms");
                String message = sb.toString();
                if (ping.channel != null)
                    Shocky.sendChannel(event.getBot(),ping.channel,message);
                else {
                    Shocky.sendNotice(event.getBot(),ping.user,message);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println("[ModulePing] recieved non-ping notice, substring result: " + pingValueString);
        }
    }
    public class CmdPing extends Command {
        public String command() {return "ping";}
        public String help(Parameters params) {
            return "ping [user] - makes the bot ping [user] (default to the person who run the command)";
        }

        public void doCommand(Parameters params, CommandCallback callback) {
            String target;
            Channel channel = params.channel;
            if (!params.hasMoreParams())
                target = params.sender.getNick();
            else
                target = params.nextParam();
            if (target.startsWith("#")) {
                callback.type = EType.Notice;
                callback.append("Target must be an user.");
                return;
            }
            if (!params.bot.userExists(target)) {
                callback.type = EType.Notice;
                callback.append("Target user does not exist.");
                return;
            }
            long timestamp = System.currentTimeMillis();
            Ping ping = new Ping(params.bot, target, params.sender, channel, timestamp, timestamp % 1000);
            params.bot.sendCTCPCommand(target,ping.toString());
            pings.put(timestamp % 1000, ping);
        }
    }

    private class Ping {
        Channel channel;
        ShockyBot bot;
        String target;
        User user;
        Long timestamp;
        Long pingValue;

        Ping(ShockyBot bot, String target, User user, Channel channel, Long timestamp, Long pingValue) {
            this.bot = bot;
            this.user = user;
            this.target = target;
            this.channel = channel;
            this.timestamp = timestamp;
            this.pingValue = pingValue;
        }

        public String toString() {
            return ("PING " + timestamp + " " + pingValue);
        }
    }
}
