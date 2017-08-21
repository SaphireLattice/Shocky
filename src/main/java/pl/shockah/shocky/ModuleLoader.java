package pl.shockah.shocky;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ModuleLoader {
	protected List<Module> modules = Collections.synchronizedList(new ArrayList<Module>());
	
	protected Module loadModule(ModuleSource<?> source) {
		Module m = load(source);
		if (m != null) modules.add(m);
		return m;
	}
	protected void unloadModule(Module module) {
		if (modules.contains(module)) {
			modules.remove(module);
			try {
				module.GetURLClassLoader().close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void unloadAllModules() {
		while (!modules.isEmpty()) modules.get(0).unload();
	}
	
	protected abstract boolean accept(ModuleSource<?> source);
	protected abstract Module load(ModuleSource<?> source);
	
	public static class Java extends ModuleLoader {
		protected boolean accept(ModuleSource<?> source) {
			return source.source instanceof File || source.source instanceof URL;
		}
		protected Module load(ModuleSource<?> source) {
			Module module = null;
			URLClassLoader tmpcl = null;
			try {
				Class<?> c = null;
				if (source.source instanceof File) {
					File file = (File)source.source;
					String moduleName = file.getName(); 
					if (moduleName.endsWith(".class")) moduleName = new StringBuilder(moduleName).reverse().delete(0,6).reverse().toString(); else return null;
					if (moduleName.contains("$")) return null;
					
					URL[] tmp = new URL[]{file.getParentFile().toURI().toURL()};
					tmpcl = new URLClassLoader(tmp);
					c = tmpcl.loadClass(moduleName);
					//tmpcl.close();
				} else if (source.source instanceof URL) {
					URL url = (URL)source.source;
					String moduleName = url.toString();
					StringBuilder sb = new StringBuilder(moduleName).reverse();
					moduleName = new StringBuilder(sb.substring(0,sb.indexOf("/"))).reverse().toString();
					String modulePath = new StringBuilder(url.toString()).delete(0,url.toString().length()-moduleName.length()).toString();
					if (moduleName.endsWith(".class")) moduleName = new StringBuilder(moduleName).reverse().delete(0,6).reverse().toString(); else return null;
					if (moduleName.contains("$")) return null;
					
					URL[] tmp = new URL[]{new URL(modulePath)};
					tmpcl = new URLClassLoader(tmp);
					c = tmpcl.loadClass(moduleName);
					//tmpcl.close();
				}
				
				if (c != null && Module.class.isAssignableFrom(c))
				{
					module = (Module)c.newInstance();
					module.SetURLClassLoader(tmpcl);
				}
			} catch (Exception e) { e.printStackTrace();}
			return module;
		}
	}
}