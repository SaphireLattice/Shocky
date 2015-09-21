package pl.shockah.shocky.threads;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.PropertyPermission;

import sun.security.util.SecurityConstants;

public class SandboxSecurityManager extends SecurityManager 
{
	public final File[] readonlyFiles;
	private final PermissionCollection allowed = new Permissions();
	private final PermissionCollection disallowed = new Permissions();
	private final String[] libs = new String[] {
			"./libs/commons-lang3-3.1.jar",
			"./libs/LOLCODE-0.11.jar",
			"./libs/luaj-jse-2.0.2.jar",
			"./libs/pircbotx-1.8.jar",
	};
	
	private final File phpData = new File("data", "php").getAbsoluteFile();
	private String tmpDir;
	
	public SandboxSecurityManager() {
		this(new File[0]);
	}
	
	public SandboxSecurityManager(File... readonlyFiles) {
		super();
		
		try {
			tmpDir = new File(System.getProperty("java.io.tmpdir")).getCanonicalPath();
		} catch (IOException e) {
			e.printStackTrace();
			tmpDir = System.getProperty("java.io.tmpdir");
		}
		
		this.readonlyFiles = readonlyFiles;
		allowed.add(new FilePermission(System.getProperty("java.home").replace('\\','/')+"/lib/-","read"));
		allowed.add(new FilePermission("/usr/lib/jvm/java-8-oracle/jre/lib/security/cacerts","read"));
		
		//String cd = '/'+System.getProperty("user.dir").replace('\\','/');
		for (int i = 0; i <libs.length; i++) {
			String lib = libs[i];
			allowed.add(new FilePermission(lib,"read"));
			allowed.add(new FilePermission(lib+"/-","read"));
		}
		
		if (this.readonlyFiles != null) {
			for (int i = 0; i < this.readonlyFiles.length; i++) {
				allowed.add(new FilePermission(this.readonlyFiles[i].getPath(),"read"));
				allowed.add(new FilePermission(this.readonlyFiles[i].getPath()+"/-","read"));
			}
		}
		
		allowed.add(new FilePermission(phpData.getPath(),"read,write"));
		allowed.add(new FilePermission(phpData.getPath()+"/-","read,write"));
		
		allowed.setReadOnly();
		
		disallowed.add(new PropertyPermission("*","write"));
	
		disallowed.add(new FilePermission("<<ALL FILES>>" ,"read,write,execute,delete"));
		
		disallowed.add(new RuntimePermission("setSecurityManager"));
		disallowed.add(new RuntimePermission("shutdownHooks"));
		disallowed.add(new RuntimePermission("accessClassInPackage.sun.reflect"));
		disallowed.add(new RuntimePermission("accessClassInPackage.sun.misc"));
		
		disallowed.add(SecurityConstants.GET_CLASSLOADER_PERMISSION);
		disallowed.add(SecurityConstants.STOP_THREAD_PERMISSION);
		//disallowed.add(SecurityConstants.DO_AS_PRIVILEGED_PERMISSION);
		
		disallowed.setReadOnly();
	}
	
	@Override
	public void checkPermission(Permission perm) 
	{
		if (!(getThreadGroup() instanceof SandboxThreadGroup))
			return;
		
		boolean disallow = disallowed.implies(perm);
		boolean allow = allowed.implies(perm);
		
		if (disallow) {
			if (!allow)
				throw new SecurityException("Not allowed: "+perm.toString());
		}
	}
	
	@Override
	public void checkRead(String file) {
		if ((getThreadGroup() instanceof SandboxThreadGroup) && isTempPaste(file))
			return;
		super.checkRead(file);
	}
	
	@Override
	public void checkWrite(String file) {
		if ((getThreadGroup() instanceof SandboxThreadGroup) && isTempPaste(file))
			return;
		super.checkWrite(file);
	}
	
	@Override
	public void checkDelete(String file) {
		if ((getThreadGroup() instanceof SandboxThreadGroup) && isTempPaste(file))
			return;
		super.checkWrite(file);
	}
	
	public boolean isTempPaste(String file) {
		File f = new File(file);
		String path;
		try {
			path = f.getCanonicalPath();
		} catch (IOException e) {
			path = f.getPath();
		}
		
		if (path.indexOf(tmpDir) != 0)
			return false;
		String name = f.getName();
		return name.startsWith("shocky_paste") && name.endsWith(".txt");
	}
	
	@Override
	public void checkExit(int status) {
		if (!(getThreadGroup() instanceof SandboxThreadGroup))
			return;
		throw new SecurityException();
	}

	@Override
	public void checkPackageAccess(String pkg) {
		if (getThreadGroup() instanceof SandboxThreadGroup) {
			if (
					pkg.startsWith("java.util.concurrent")||
					pkg.startsWith("java.lang.reflect")
				)
				throw new SecurityException(pkg+" package is not allowed.");
		}
		super.checkPackageAccess(pkg);
	}

	@Override
	public void checkAccess(Thread t) {
		Thread parent = Thread.currentThread();
		if (!(parent.getThreadGroup() instanceof SandboxThreadGroup))
			return;
		if (t.getThreadGroup() instanceof SandboxThreadGroup)
			return;
		Thread.dumpStack();
		throw new SecurityException("Sandboxed threads must remain in sandbox thread group.");
	}
}
