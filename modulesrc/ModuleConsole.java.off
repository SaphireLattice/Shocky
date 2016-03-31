import java.io.File;
import java.io.IOException;

import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import pl.shockah.shocky.Module;

public class ModuleConsole extends Module {
	private static ThreadConsoleInput ConsoleThread = new ThreadConsoleInput();
	public static Terminal terminal;
	public static Screen screen;
	
	public String name() {
		return "console";
	}
	
	public void onEnable(File dir) {
		ConsoleThread.start();
	}
	
	public void onDisable() {
		try {
			terminal.clearScreen();
			terminal.setCursorPosition(0, 0);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			screen = null;
			terminal = null;
		}
	}
	
	private static class ThreadConsoleInput extends Thread {
		public void run() {
			try{
				terminal = new DefaultTerminalFactory().createTerminal();
				screen = new TerminalScreen(terminal);
				Panel cmd = new Panel();
				TextBox cmd_tb = new TextBox();
				cmd.addComponent(cmd_tb);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
