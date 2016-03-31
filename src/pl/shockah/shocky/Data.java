package pl.shockah.shocky;

import java.io.Console;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;

import pl.shockah.Config;
import pl.shockah.FileLine;
import pl.shockah.StringTools;
import pl.shockah.shocky.lines.Line;
import pl.shockah.shocky.lines.LineAction;
import pl.shockah.shocky.lines.LineEnterLeave;
import pl.shockah.shocky.lines.LineKick;
import pl.shockah.shocky.lines.LineMessage;
import pl.shockah.shocky.lines.LineMode;
import pl.shockah.shocky.lines.LineOther;

import static java.util.Collections.addAll;

public class Data {
    public static final Config config = new Config();
    public static final ArrayList<String> controllers = new ArrayList<String>();
    public static final HashMap<Integer, ArrayList<String>> channels = new HashMap<>();
    public static final ArrayList<String> blacklistNicks = new ArrayList<String>();
    public static final ArrayList<String> protectedKeys = new ArrayList<String>();
    public static final File saveDir = new File("data", "saves");
    public static final File lastSave = new File(saveDir, "last");

    static {
        initializeLineTypes();
    }

    private static synchronized void blank() {
        Data.config.setNotExists("main-botname","Shocky");
        Data.config.setNotExists("main-server","irc.esper.net");
        Data.config.setNotExists("main-version","Shocky - PircBotX "+PircBotX.VERSION+" - https://github.com/clone1018/Shocky - http://pircbotx.googlecode.com");
        Data.config.setNotExists("main-verbose",false);
        Data.config.setNotExists("main-maxchannels",10);
        Data.config.setNotExists("main-nickservpass","");
        Data.config.setNotExists("main-cmdchar","`~");
        Data.config.setNotExists("main-messagelength",400);
        Data.config.setNotExists("main-messagedelay",500);
        Data.config.setNotExists("main-saveinterval",300);
        Data.config.setNotExists("main-backups",2);
        Data.config.setNotExists("main-sqlurl","http://localhost/shocky/sql.php");
        Data.config.setNotExists("main-sqlhost","localhost");
        Data.config.setNotExists("main-sqluser","");
        Data.config.setNotExists("main-sqlpass","");
        Data.config.setNotExists("main-sqldb","shocky");
        Data.config.setNotExists("main-sqlprefix","");
        Data.config.setNotExists("main-bitlyuser","");
        Data.config.setNotExists("main-bitlyapikey","");

        protectedKeys.addAll(Arrays.asList(new String[]{
            "main-botname","main-server","main-version","main-verborse","main-maxchannels","main-nickservpass","main-messagelength","main-messagedelay","main-saveinterval",
            "main-sqlurl","main-sqluser","main-sqlpass","main-sqldb","main-sqlprefix","main-bitlyuser","main-bitlyapikey"
        }));

        Console c = System.console();
        if (c == null) System.out.println("--- Not running in console, using default first-run settings ---"); else {
            System.out.println("--- First-run setup ---\n(just press Enter for default value)\n");

            firstRunSetupString(c,"main-server");
            firstRunSetupBoolean(c,"main-verbose");
            firstRunSetupString(c,"main-botname");
            firstRunSetupInt(c,"main-messagedelay");
            firstRunSetupPassword(c,"main-nickservpass");
            firstRunSetupString(c,"main-version");
            firstRunSetupInt(c,"main-maxchannels");
            firstRunSetupString(c,"main-cmdchar");
            firstRunSetupInt(c,"main-messagelength");
            firstRunSetupInt(c,"main-saveinterval");
            firstRunSetupString(c,"main-sqlurl");
            firstRunSetupString(c,"main-sqlhost");
            firstRunSetupString(c,"main-sqluser");
            firstRunSetupPassword(c,"main-sqlpass");
            firstRunSetupString(c,"main-sqldb");
            firstRunSetupString(c,"main-sqlprefix");
            System.out.println();
        }
    }
    protected static synchronized void load() {
        lastSave.mkdir();

        config.load(new File(lastSave,"config.cfg"));
        if (new File(lastSave,"config.cfg").exists()) {
            controllers.addAll(FileLine.read(new File(lastSave,"controllers.cfg")));
            for (int i = 1; i <= config.getInt("main-servers"); i++) {
                ArrayList<String> c = new ArrayList<>();
                c.addAll(FileLine.read(new File(lastSave,"channels_" + i + ".cfg")));
                channels.put(i, c);
            }
            blacklistNicks.addAll(FileLine.read(new File(lastSave,"blacklistNicks.cfg")));
        } else blank();

        protectedKeys.add("main-bitlyuser");
        protectedKeys.add("main-bitlyapikey");
        Data.config.setNotExists("main-backups",2);
    }

    protected static synchronized void save() {
        int backups = Data.config.getInt("main-backups");

        saveDir.mkdirs();
        File[] dirs = new File[backups+1];
        dirs[0] = lastSave;
        for (int i = 1; i <= backups; ++i)
            dirs[i]=new File(saveDir,"backup"+Integer.toString(i));

        for (int i = dirs.length-1; i > 0; --i)
        {
            File from = dirs[i-1];
            File to = dirs[i];
            if (from.isDirectory())
                moveDir(from,to);
        }

        File dir = dirs[0];
        dir.mkdir();
        config.save(new File(dir,"config.cfg"));
        FileLine.write(new File(dir,"controllers.cfg"),controllers);
        int i = 1;
        for (ArrayList<String> channels_sublist: channels.values()) {
            FileLine.write(new File(dir,"channels_" + i + ".cfg"),channels_sublist);
            i++;
        }
        FileLine.write(new File(dir,"blacklistNicks.cfg"),blacklistNicks);

        for (Module module : Module.getModules()) {
            try {
                module.onDataSave(dir);
            } catch (Exception e) {
                System.out.println(module.name());
                e.printStackTrace();
            }
        }
    }

    private static boolean moveDir(File from, File to) {
        if (!from.isDirectory())
            return false;
        to.mkdir();
        URI fromURI = from.toURI();
        URI toURI = to.toURI();
        File[] files = from.listFiles();
        boolean success = true;
        for (int i = 0; success && i < files.length; ++i) {
            File file = files[i];
            URI oldURI = fromURI.relativize(file.toURI());
            URI newURI = toURI.resolve(oldURI);
            File newfile = new File(newURI);
            if (file.isDirectory())
                success = moveDir(file,newfile);
            else {
                if (newfile.isFile())
                    success = newfile.delete();
                if (success)
                    success = file.renameTo(newfile);
            }

        }
        if (success)
            success = from.delete();
        return success;
    }

    private static void firstRunSetupString(Console c, String key) {
        String input = c.readLine(key+" (def: "+Data.config.getString(key)+"): ");
        if (input != null && !input.isEmpty()) Data.config.set(key,input);
    }
    private static void firstRunSetupPassword(Console c, String key) {
        String input = new String(c.readPassword(key+" (def: "+Data.config.getString(key)+"): "));
        if (input != null && !input.isEmpty()) Data.config.set(key,input);
    }
    private static void firstRunSetupBoolean(Console c, String key) {
        while (true) {
            String input = c.readLine(key+" (def: "+Data.config.getString(key)+"): ");
            if (input != null && !input.isEmpty()) {
                if (input.toLowerCase().matches("^(t(rue)?)|(f(alse)?)$")) {
                    Data.config.set(key,input);
                    return;
                }
            } else return;
        }
    }
    private static void firstRunSetupInt(Console c, String key) {
        while (true) {
            String input = c.readLine(key+" (def: "+Data.config.getString(key)+"): ");
            if (input != null && !input.isEmpty()) {
                if (StringTools.isNumber(input)) {
                    Data.config.set(key,input);
                    return;
                }
            } else return;
        }
    }
    public static void initializeLineTypes() {
        Line.registerLineType((byte) 0, LineOther.class);
        Line.registerLineType((byte) 1, LineMessage.class);
        Line.registerLineType((byte) 2, LineAction.class);
        Line.registerLineType((byte) 3, LineEnterLeave.class);
        Line.registerLineType((byte) 4, LineKick.class);
        Line.registerLineType((byte) 5, LineMode.class);
    }
    public static boolean isBlacklisted(User user) {
        start: for (int i = 0; i < blacklistNicks.size(); i++) {
            String blacklisted = blacklistNicks.get(i);
            char type = 'n';
            if (blacklisted.charAt(1) == ':') {
                String[] blacklistParts = blacklisted.split(":", 2);
                type = blacklistParts[0].charAt(0);
                blacklisted = blacklistParts[1];
            }
            String[] array = blacklisted.split("\\*");
            String value = "";
            int o = 0;
            switch (type) {
            case 'n':
                value = user.getNick();
                break;
            case 'h':
                value = user.getHostmask();
                break;
            case 'i':
                value = user.getLogin();
                break;
            case 's':
                value = Whois.getWhoisLogin(user);
                break;
            }
            if (value == null)
                continue start;
            value = value.toLowerCase();
            for (String part : array) {
                int idx = value.indexOf(part, o);
                if (idx == -1)
                    continue start;
                o += idx;
            }
            return true;
        }

        return false;
    }

    public static Config forChannel(String chan) {
        return Data.config.getConfig(chan);
    }
    public static Config forChannel(Channel chan) {
        if (chan == null) return Data.config;
        return forChannel(chan.getName());
    }
}