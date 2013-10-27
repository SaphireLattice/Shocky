import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.pircbotx.Colors;
import org.pircbotx.ShockyBot;
import org.pircbotx.hooks.events.MessageEvent;

import pl.shockah.StringTools;
import pl.shockah.shocky.Data;
import pl.shockah.shocky.Module;
import pl.shockah.shocky.Shocky;
import pl.shockah.shocky.interfaces.IRollback;
import pl.shockah.shocky.interfaces.ILinePredicate;
import pl.shockah.shocky.lines.LineAction;
import pl.shockah.shocky.lines.LineMessage;
import pl.shockah.shocky.lines.LineWithUsers;

public class ModuleRegexReplace extends Module {

	public static String[] groupColors = new String[] { Colors.BLUE + ",02",Colors.RED + ",05", Colors.GREEN + ",03", Colors.MAGENTA + ",06",Colors.CYAN + ",10" };

	@Override
	public String name() {return "regexreplace";}
	public boolean isListener() {return true;}

	@Override
	public void onMessage(MessageEvent<ShockyBot> event) throws Exception {
		if (Data.isBlacklisted(event.getUser()))
			return;
		IRollback module = (IRollback) Module.getModule("rollback");
		if (module == null)
			return;
		String s = event.getMessage().trim();
		if (!s.startsWith("s/") && !s.startsWith("m/"))
			return;
		String[] args = s.split("(?<!\\\\)/", -1);
		boolean replace = args[0].contentEquals("s");
		if (!replace && event.getChannel().getMode().contains("c"))
			return;

		int flagPos = 2;
		if (replace)
			flagPos = 3;

		if (args.length != flagPos + 1)
			return;
		if (args[1].isEmpty())
			return;

		String[] params = args[flagPos].split(" ", -1);
		String user = null;

		int flags = 0;
		boolean single = true;
		if (params.length > 0) {
			for (char c : params[0].toCharArray()) {
				switch (c) {
				case 'd':flags |= Pattern.UNIX_LINES;break;
				case 'g':single = false;break;
				case 'i':flags |= Pattern.CASE_INSENSITIVE;break;
				case 'm':flags |= Pattern.MULTILINE;break;
				case 's':flags |= Pattern.DOTALL;break;
				case 'u':flags |= Pattern.UNICODE_CASE;break;
				case 'x':flags |= Pattern.COMMENTS;break;
				}
			}

			if (params.length > 1)
				user = params[1];
		}
		Pattern pattern;
		try {
			pattern = Pattern.compile(args[1], flags);
		} catch (PatternSyntaxException e) {
			Shocky.sendChannel(event.getBot(), event.getChannel(), StringTools.deleteWhitespace(e.getMessage()));
			return;
		}
		
		final ExecutorService service = Executors.newFixedThreadPool(1);
		try {
			Future<String> run = service.submit(new Run(module, pattern, event.getChannel().getName(), user, s, single, replace ? args[2] : null));
			String output = run.get(10, TimeUnit.SECONDS);
			if (output != null)
				Shocky.sendChannel(event.getBot(), event.getChannel(), output);
		} catch (TimeoutException e) {
		} finally {
			service.shutdown();
		}
	}

	private static class Run implements Callable<String>, ILinePredicate<LineWithUsers> {
		private final IRollback module;
		private final Matcher matcher;
		private final String channel;
		private final String user;
		private final String message;
		private final boolean single;
		private final String replacement;

		public Run(IRollback module, Pattern pattern, String channel, String user, String message, boolean single, String replacement) {
			this.module = module;
			this.matcher = pattern.matcher("");
			this.channel = channel;
			this.user = user;
			this.message = message;
			this.single = single;
			this.replacement = replacement;
		}

		@Override
		public boolean accepts(LineWithUsers line) {
			String text;
			if (line instanceof LineMessage)
				text = ((LineMessage) line).text;
			else if (line instanceof LineAction)
				text = ((LineAction) line).text;
			else
				return false;
			if (replacement == null)
				text = Colors.removeFormattingAndColors(text);
			return matcher.reset(text).find();
		}

		@Override
		public String call() throws Exception {
			LineWithUsers line = module.getRollbackLine(this, LineWithUsers.class, channel, user, null, message, true, 10, 0);
			if (line == null)
				return null;
			StringBuffer sb = new StringBuffer();
			do {
				if (replacement != null)
					matcher.appendReplacement(sb, replacement);
				else {
					String capture = matcher.group();
					char[] chars = capture.toCharArray();
					StringBuilder sb2 = new StringBuilder();
					Stack<Integer> color = new Stack<Integer>();
					int last = -1;
					for (int o = 0; o <= chars.length; o++) {
						for (int p = 0; p <= matcher.groupCount(); p++) {
							int s = matcher.start(p) - matcher.start();
							int e = matcher.end(p) - matcher.start();
							if (o == s)
								color.push(p);
							else if (o == e)
								color.pop();
						}

						if (color.isEmpty() && last != -1) {
							last = -1;
							sb2.append(Colors.NORMAL);
						} else if (!color.isEmpty() && last != color.peek()) {
							last = color.peek();
							sb2.append(groupColors[last% groupColors.length]);
						}

						if (o < chars.length)
							sb2.append(chars[o]);
					}
					matcher.appendReplacement(sb, sb2.toString());
				}
				if (single)
					break;
			} while (matcher.find());
			matcher.appendTail(sb);
			if (line instanceof LineAction) {
				sb.insert(0, "\001ACTION ");
				sb.append('\001');
			}
			return StringTools.limitLength(sb);
		}
	}
}
