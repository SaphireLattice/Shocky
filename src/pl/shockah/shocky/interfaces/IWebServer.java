package pl.shockah.shocky.interfaces;

import java.io.File;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;

public interface IWebServer {
	public HttpContext addPaste(File file);

	public String getURL();

	public String getURL(String path);

	public HttpContext addRedirect(String url);
	
	public boolean exists();

	public HttpContext createContext(String url, HttpHandler handler);

    public boolean removeContext(HttpContext context);

    public boolean removeContext(String contextURL);
}
