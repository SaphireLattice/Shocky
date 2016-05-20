import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpContext;

import pl.shockah.HTTPQuery;
import pl.shockah.Helper;
import pl.shockah.shocky.Data;
import pl.shockah.shocky.Module;
import pl.shockah.shocky.interfaces.IPaste;
import pl.shockah.shocky.interfaces.IWebServer;



public class ModulePaste extends Module implements IPaste {
	
	public static final List<PasteService> services = new LinkedList<PasteService>();

	@Override
	public String name() {
		return "paste";
	}
	
	public void onEnable(File f) {
		initPasteServices();
	}
	
	public void initPasteServices() {
		String key = null;
		services.clear();
		services.add(new ServicePasteKdeOrg());
		key = Data.config.getString("api-pastebin.com");
		if (key != null)
			services.add(new ServicePastebinCom(key));
		key = Data.config.getString("api-pastebin.ca");
		if (key != null)
			services.add(new ServicePastebinCa(key));
	}
	
	public String paste(CharSequence data) {
		IWebServer ws = null;
		try {
			ws = (IWebServer) Module.getModule("webserver");
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (ws!=null && ws.exists() && data.length() < 5242880)
		{
			File file;
			try {
				file = File.createTempFile("shocky_paste", ".txt");
				file.deleteOnExit();
				FileOutputStream os = new FileOutputStream(file);
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os,Helper.utf8));
				bw.append(data);
				bw.close();
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
				file = null;
			}
			
			if (file != null) {
				HttpContext context = ws.addPaste(file);
				return ws.getURL(context.getPath());
			}
		}
		String link = null;
		for (PasteService service : services) {
			link = service.paste(data);
			if (link == null) continue;
			if (link.isEmpty() || link.startsWith("http://")) break;
		}
		return link;
	}
	
	public interface PasteService {
		String paste(CharSequence data);
	}
	
	public static class ServicePastebinCa implements PasteService {
		
		private final String apiKey;
		
		public ServicePastebinCa(String apiKey) {
			this.apiKey = apiKey;
		}
		
		public String paste(CharSequence data) {
			HTTPQuery q;
			try {
				q = HTTPQuery.create("http://pastebin.ca/quiet-paste.php",HTTPQuery.Method.POST);
			} catch (MalformedURLException e1) {return null;}
			
			StringBuilder sb = new StringBuilder(data.length()+50);
			try {
				sb.append("api=").append(URLEncoder.encode(apiKey,"UTF8"));
				sb.append("&content=");
				sb.append(data);
			} catch (Exception e) {e.printStackTrace();}
			
			q.connect(true,true);
			q.write(sb.toString());
			ArrayList<String> list = q.readLines();
			q.close();
			
			String s = list.get(0);
			if (s.startsWith("SUCCESS")) return "http://pastebin.ca/"+s.substring(7);
			return null;
		}
	}
	
	public static class ServicePastebinCom implements PasteService {
		
		private final String apiKey;
		
		public ServicePastebinCom(String apiKey) {
			this.apiKey = apiKey;
		}
		
		public String paste(CharSequence data) {
			HTTPQuery q;
			try {
				q = HTTPQuery.create("http://pastebin.com/api/api_post.php",HTTPQuery.Method.POST);
			} catch (MalformedURLException e) {return null;}
			
			StringBuilder sb = new StringBuilder(data.length()+150);
			sb.append("api_option=paste");
			sb.append("&api_dev_key=").append(apiKey);
			sb.append("&api_paste_private=1");
			sb.append("&api_paste_format=text");
			sb.append("&api_paste_code=");
			sb.append(data);
			
			q.connect(true,true);
			q.write(sb.toString());
			ArrayList<String> list = q.readLines();
			q.close();
			
			return list.get(0);
		}
	}
	
	public static class ServicePasteKdeOrg implements PasteService {
		
		public String paste(CharSequence data) {
			HTTPQuery q;
			try {
				q = HTTPQuery.create("http://pastebin.kde.org/api/json/create",HTTPQuery.Method.POST);
			} catch (MalformedURLException e1) {return null;}
			
			StringBuilder sb = new StringBuilder(data.length()+32);
			sb.append("language=text&private=true&data=").append(data);
			
			q.connect(true,true);
			q.write(sb.toString());
			JSONObject json;
			try {
				json = new JSONObject(q.readWhole()).getJSONObject("result");
				if (json.has("error")) {
					System.out.println(json.getString("error"));
					return null;
				}
				
				String pasteId = json.getString("id");
				String pasteHash = json.getString("hash");
				
				return "http://pastebin.kde.org/"+pasteId+"/"+pasteHash;
			} catch (JSONException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				q.close();
			}
			return null;
		}
	}
}