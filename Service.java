import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;
import java.util.*;
public class Service implements Runnable {
	
	// The tcp port send HTTP requests to for this service
	private int port;
	
	// The command to run and the directory to run it in
	private String command;
	private File workingdir;
	
	// The name of this service (for internal use)
	private final String name;
	private boolean run = false;  // Whether the service should be running
	private boolean running = false; // Whether the service is running
	private Queue<String> stdOut = new LinkedList<String>();
	private Queue<String> stdErr = new LinkedList<String>();
	private Process currentProcess;
	private Thread currentThread;
	private static Collection<Service> services = new LinkedList<Service>();
	private static Map<Integer, Service> portmap =  new HashMap<Integer, Service>();
	private Map<String, Command> commands = new HashMap<String, Command>();
	public Service(int port, String path, String command, String name) {
		this.port = port;
		this.workingdir = new File (Manager.getSetting("root_path", ""), path);
		this.command = command;
		this.name = name;
		portmap.put(port, this);
		services.add(this);
		commands.put("clearlog", new ClearLogCommand(this));
		
		// If the port matches the currently running port, then the service is already running
		if (port == Manager.getPort()) {
			run = true;
			running = true;
			
			// Otherwise start the service (in a new thread)
		} else {
			commands.put("start", new StartCommand(this));
			commands.put("stop", new StopCommand(this));
			commands.put("restart", new RestartCommand(this));
			start();
		}
	}
	public void run() {
		
		// Don't let each service run more than once concurrently
		if (running) return;
		if (!run) return;
		
		
		try {
			final Process process = Runtime.getRuntime().exec(command, null, workingdir);
			currentProcess = process;
			running = true;
			
			final BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
			final BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			try {
				
				
				// Destroy the service's process on shutdown
				Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
					public void run() {
						Manager.log("Shutting down service "+name);
						process.destroy();
						try {
							stdInput.close();
						} catch (IOException e) {
							Manager.logErr("Failed to close stdInput for "+name);
							Manager.logErr(e);
						}
						try {
							stdError.close();
						} catch (IOException e) {
							Manager.logErr("Failed to close stdError for "+name);
							Manager.logErr(e);
						}
						
						
						running = false;
					}
				}));
				
				new Thread(new Runnable() {
					public void run() {
						try {
							String s = null;
							while ((s = stdInput.readLine()) != null) {
								log(s);
							}
						} catch (IOException e) {
							Manager.logErr("Can't read from "+name+", IOException.");
							Manager.logErr(e);
						}
					}
				}).start();
				
				new Thread(new Runnable() {
					public void run() {
						try {
							String s = null;
							while ((s = stdError.readLine()) != null) {
								
								// HACK: Old version of django doesn't give much control on logging and spews stuff out to stderr.  Send everything to stdout
								if (name.equals("Contacts")) {
									log(s);
									continue;
								}
								logErr(s);
							}
						} catch (IOException e) {
							Manager.logErr("Can't read err from "+name+", IOException.");
							Manager.logErr(e);
						}
					}
				}).start();
				
				
				process.waitFor();
			} catch (IllegalStateException e) {
				
				// This occurs when the JVM is shutting down, in which case don't bother trying to restart anything
				return;
			} catch (InterruptedException e) {
				Manager.logErr("Service "+name+" interrupted");
				Manager.logErr(e);
			}
			running = false;
			if (run) {
				Manager.log("Service "+name+" stopped, restarting in 30 seconds...");
				try {
					Thread.sleep(30*1000);
				} catch (InterruptedException e) {
					Manager.logErr("Sleep interrupted, going straight to restart...");
					Manager.logErr(e);
				}
			}
			// Tidy up the old process and any pipes left open before restarting the process
			process.destroy();
			try {
				stdInput.close();
			} catch (IOException e) {
				Manager.logErr("Failed to close stdInput for "+name);
				Manager.logErr(e);
			}
			try {
				stdError.close();
			} catch (IOException e) {
				Manager.logErr("Failed to close stdError for "+name);
				Manager.logErr(e);
			}
			// If the service is set to run, restart the process (NB: this recursive call can cause horrible stack traces)
			if (run) {
				run();
			} else {
				Manager.log("Service "+name+" stopped.");
			}
			
		} catch (IOException e) {
			Manager.logErr("Process "+name+" didn't load due to IOException");
			Manager.logErr(e);
		}
		
	}
	public int getPort() {
		return port;
	}
	public String getName() {
		return name;
	}
	public File getWorkingDir() {
		return workingdir;
	}
	public void log(String line) {
		int outputLength;
		try {
			outputLength = Integer.parseInt(Manager.getSetting("output_length"));
		} catch (NumberFormatException e) {
			outputLength = 10;
		}
		stdOut.add(line);
		while (stdOut.size() > outputLength) stdOut.remove();
	}
	public void logErr(String line) {
		int outputLength;
		try {
			outputLength = Integer.parseInt(Manager.getSetting("output_length"));
		} catch (NumberFormatException e) {
			outputLength = 10;
		}
		stdErr.add(line);
		while (stdErr.size() > outputLength) stdErr.remove();
	}
	public void logErr(Exception e) {
		Writer writer = new StringWriter();
		PrintWriter printWriter = new PrintWriter(writer);
		e.printStackTrace(printWriter);
		logErr(writer.toString());
	}
	void start() {
		if (running) return;
		run = true;
		currentThread = new Thread(this);
		currentThread.start();
		Manager.log("Starting service "+name+" ("+command+")");
	}
	void stop() {
		
		// Can't kill services module
		if (port == Manager.getPort()) return;
		if (!run) return;
		run = false;
		if (!running) return;
		currentProcess.destroy();
		Manager.log(name +" process destroyed");
	}
	void clearLog() {
		stdErr.clear();
		stdOut.clear();
	}
	public boolean execCommand(String key) {
		Command command = commands.get(key);
		if (command == null) return false;
		Thread thread = new Thread(command);
		thread.start();
		return true;
	}
	public void addCommand(String key, String cmd, String name) {
		Command command = new Command(this, cmd, name);
		commands.put(key, command);
	}
	public boolean isRunning() {
		return running;
	}
	public boolean hasError() {
		return (stdErr.size() > 0);
	}
	public Iterator getDataIterator() {
		Map<String, String> data =  new HashMap<String, String>();
		data.put("port", ""+port);
		data.put("path", workingdir.getPath());
		data.put("command", command);
		data.put("name", name);
		data.put("running", (running)?"running":"stopped");
		Iterator iter;
		
		iter = stdOut.iterator();
		String stdOutContent = "";
		while (iter.hasNext()) {
			stdOutContent += (String)iter.next();
			stdOutContent += "\n";
		}
		data.put("stdOut", stdOutContent);
		
		iter = stdErr.iterator();
		String stdErrContent = "";
		while (iter.hasNext()) {
			stdErrContent += (String)iter.next();
			stdErrContent += "\n";
		}
		data.put("stdErr", stdErrContent);
		
		
		
		String commandlist = "<ul id='commandlist'>\n";
		iter = commands.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry header = (Map.Entry)iter.next();
			Command command = (Command)header.getValue();
			String action = "/services/"+port+"/"+header.getKey();
			commandlist += "\t<li><form method='post' action=\""+action+"\">"
			+ "<input type='submit' value=\""
			+ command.getName().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
			+ "\" /></form></li>\n";
		}
		commandlist += "</ul>";
		data.put("commandlist_html", commandlist);
		
		
		return data.entrySet().iterator();
	}
	public static Service getByPort(int port) {
		return portmap.get(port);
	}
	public static Iterator getAllDataIterator() {
		Map<String, String> data =  new HashMap<String, String>();
		String servicelist = "<ul id='servicelist'>\n";
		Iterator iter = services.iterator();
		while (iter.hasNext()) {
			Service service = (Service)iter.next();
			servicelist += "\t<li class=\""
			+((service.isRunning())?"running":"stopped")
			+((service.hasError())?" error":"")
			+"\"><a href=\"/services/"+service.getPort()+"\">"
			+ service.getName().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
			+ "</a></li>\n";
		}
		servicelist += "</ul>";
		data.put("servicelist_html", servicelist);
		return data.entrySet().iterator();
	}
	
}
