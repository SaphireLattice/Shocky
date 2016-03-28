package pl.shockah.shocky.interfaces;

import java.io.File;

import com.sun.net.httpserver.HttpContext;

public interface IWebServer {
	public HttpContext addPaste(File file);

	public String getURL();

	public HttpContext addRedirect(String url);
	
	public boolean exists();
}
