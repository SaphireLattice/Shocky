package org.pircbotx;

import javax.net.SocketFactory;
import java.util.*;

public class ShockyMultiBotManager extends MultiBotManager {
    protected final Set<ShockyBotEntry> bots = new HashSet();
	public ShockyMultiBotManager(String name) {
		super(name);
	}

    public Set<ShockyBot> getShockyBots() {
        Set<ShockyBot> actualBots = new HashSet();
        for (ShockyBotEntry curEntry : bots)
            actualBots.add(curEntry.getBot());
        return Collections.unmodifiableSet(actualBots);
    }

    public ShockyMultiBotManager(ShockyBot dummyBot) {
		super(dummyBot);
	}

	@SuppressWarnings("unchecked")
	public ShockyBotBuilder createBot(String hostname, int port, String password, SocketFactory socketFactory, int id)
    {
    	ShockyBot bot = new ShockyBot(id);
        bot.setListenerManager(listenerManager);
        bot.setName(name);
        bot.setVerbose(verbose);
        bot.setSocketTimeout(socketTimeout);
        bot.setMessageDelay(messageDelay);
        bot.setLogin(login);
        bot.setAutoNickChange(autoNickChange);
        bot.setEncoding(encoding);
        bot.setDccInetAddress(dcciNetAddress);
        bot.setDccPorts(dccports);
        
        ShockyBotBuilder builder = new ShockyBotBuilder(bot);
		bots.add(new ShockyBotEntry(bot, hostname, port, password, socketFactory, builder));
		return builder;
    }

    public static class ShockyBotEntry extends BotEntry {
        protected final ShockyBot bot;

        public ShockyBotEntry(ShockyBot bot, String hostname, int port, String password, SocketFactory socketFactory, ShockyBotBuilder builder) {
            super(bot, hostname, port, password, socketFactory, builder);
            this.bot = bot;
        }

        @Override
        public ShockyBot getBot() {
            return this.bot;
        }
    }

    public static class ShockyBotBuilder extends BotBuilder {
        protected ShockyBot bot;
        protected Map<String, String> channels = new HashMap();

        public ShockyBotBuilder addChannel(String channelName) {
            this.channels.put(channelName, "");
            return this;
        }

        public ShockyBotBuilder addChannel(String channelName, String key) {
            this.channels.put(channelName, key);
            return this;
        }

        public ShockyBotBuilder(ShockyBot bot) {
            super(bot);
            this.bot = bot;
        }

        public ShockyBot getBot() {
            return this.bot;
        }

        protected Map<String, String> getChannels() {
            return this.channels;
        }
    }
}
