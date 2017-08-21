package pl.shockah.shocky;

import java.util.*;

import org.pircbotx.*;
import org.pircbotx.ShockyMultiBotManager.ShockyBotBuilder;

import pl.shockah.Reflection;

public class MultiChannel {
	private static List<ShockyBot> botList = new LinkedList<>();
	protected static String channelPrefixes = null;
	
	public static Channel get(String name) {
		name = name.toLowerCase();
		synchronized (botList) {
			for (PircBotX entry : botList) {
				for (Channel channel : entry.getChannels()) {
					if (channel.getName().equalsIgnoreCase(name))
						return channel;
				}
			}
		}
		return null;
	}

	private static ShockyBot createBot(int id) {
        System.out.println("MultiChannel.createBot: Creating ShockyBot #" + id);
        ShockyBot bot = null;
		ShockyBotBuilder builder;
		try {
			builder = Shocky.getBotManager().createBot(Data.config.getString(id+"-server"), Data.config.getInt(id+"-port"), Data.config.getString(id+"-pass"), null, id);
            System.out.println("MultiChannel.createBot: Created ShockyBotBuilder #" + id);
			bot = builder.getBot();
			bot.setVersion(Data.config.getString(id+"-version"));
			if (!connect(bot, id)) {
                System.out.println("MultiChannel.createBot: Failed to connect bot #" + id);
				return null;
			}
			if (channelPrefixes == null)
				channelPrefixes = Reflection.getPrivateValue(PircBotX.class,"channelPrefixes",bot);
			botList.add(bot);
            bot.setID(id);
		} catch(Exception e) { e.printStackTrace();}
		return bot;
	}
	
	public static boolean connect(ShockyBot bot, int id) {
        System.out.println("MultiChannel.connect: Connecting bot #" + id);
        String server = Data.config.getString(id + "-server");
        Integer port = Data.config.getInt(id + "-port");
        String pass = Data.config.getString(id + "-pass");
        try {
            bot.connect(server.equalsIgnoreCase("localhost") ? null : server, port, pass);
            if (!bot.isConnected())
                return false;
            if (!Data.config.getString(id+"-nickservpass").isEmpty())
                bot.identify(Data.config.getString(id+"-nickservpass"));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
	}
	
	public static List<String> getBotChannels(ShockyBot bot) throws Exception {
		Set<Channel> set = bot.getChannels();
		List<String> list = new ArrayList<String>(set.size());
		for (Channel channel : set)
			list.add(channel.getName());
		return Collections.unmodifiableList(list);
	}
	
	public static void lostChannel(String channel) throws Exception {
		Data.channels.remove(channel);
	}
	
	public static void join(int id ,String... channels) throws Exception {
        ShockyBot bot;
        if (botList.size() < id)
            bot = createBot(id);
        else
            bot = botList.get(id - 1);
		join(bot, id, channels);
	}
	
	public synchronized static void join(ShockyBot bot, int id, String... channels) throws Exception {
        System.out.println("MultiChannel.join: Joining bot #" + id);
		if (channels == null || channels.length == 0) {
            System.out.println("MultiChannel.join: No channels for bot #" + id);
            return;
        }
		List<Thread> joinThreads = new LinkedList<>();
		synchronized (bot) {
			List<String> currentChannels = new LinkedList<>();
            currentChannels.addAll(getBotChannels(bot));
		
			List<String> joinList = new LinkedList<>(Arrays.asList(channels));
			joinList.removeAll(currentChannels);
			if (joinList.size() == 0)
				return;
			
			Iterator<String> joinIter = joinList.iterator();
            if (joinIter.hasNext()) {
                List<String> channelList = new ArrayList<>(getBotChannels(bot));
                channelList.removeAll(joinList);
                if (!(channelList.size() >= Data.config.getInt(id + "-maxchannels")))
                    joinThreads.add(joinChannels(bot, joinIter, channelList.size(), id));
            }

			if (joinIter.hasNext()) {
                    System.out.println("MultiChannel.join: joinIter.hasNext()  ");
					Thread t = joinChannels(bot, joinIter, 0, id);
					if (t != null) {
                        joinThreads.add(t);
                        Thread.sleep(3000);
                    }
            }
		}
		for (Thread t : joinThreads)
			t.join();
		System.out.format("Finished joining %d channel%s",channels.length,channels.length==1?"":"s").println();
	}
	
	private static Thread joinChannels(PircBotX bot, Iterator<String> iter, int start, int id) {
		if (bot == null || iter == null || !iter.hasNext())
			return null;
		List<String> botChannel = new ArrayList<String>();
		for (int i = start; iter.hasNext() && i < Data.config.getInt(id+"-maxchannels"); ++i) {
			String channel = iter.next().toLowerCase();
			synchronized (Data.channels) {
				if (!Data.channels.get(id).contains(channel))
					Data.channels.get(id).add(channel);
			}
			botChannel.add(channel);
		}
		Thread t = new JoinChannelsThread(bot,botChannel.toArray(new String[0]));
		t.start();
		return t;
	}
	
	private static class JoinChannelsThread extends Thread {
		private final PircBotX bot;
		private final String[] channels;
		public JoinChannelsThread(PircBotX bot, String[] channels) {
			super();
			this.setDaemon(true);
			this.bot = bot;
			this.channels = channels;
		}

		@Override
		public void run() {
		for (int i = 0; i < channels.length; ++i) {
				String channel = channels[i];
				if (bot.channelExists(channel))
					continue;
				if (i > 0 && (i % 10 == 0)) {
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				bot.joinChannel(channel);
			}
		}
	}
	
	public static void part(int id, String... channels) throws Exception {
		List<String> argsList;
		if (channels == null || channels.length == 0)
			argsList = new LinkedList<>(Data.channels.get(id ));
		else
			argsList = Arrays.asList(channels);
		if (!Data.channels.get(id).removeAll(argsList))
			return;

        ShockyBot bot = botList.get(id - 1);
		List<String> partList = new LinkedList<>(getBotChannels(bot));
        partList.retainAll(argsList);
        for (String channel : partList)
            bot.partChannel(bot.getChannel(channel));
	}
}