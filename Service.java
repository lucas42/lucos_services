import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
public class Service implements Runnable {
	
	// The tcp port send HTTP requests to for this service
	private int port;
	
	// The command to run and the directory to run it in
	private String command;
	private File workingdir;
	
	// The name of this service (for internal use)
	private String name;
	private String id;
	private boolean run = false;  // Whether the service should be running
	private boolean running = false; // Whether the service is running
	private Queue<String> stdOut = new LinkedList<String>();
	private Queue<String> stdErr = new LinkedList<String>();
	private Process currentProcess;
	private Thread currentThread;
	private static Map<String, Service> serviceList =  new HashMap<String, Service>();
	private final boolean isMaster;
	private Map<String, Command> commands = new HashMap<String, Command>();
	private Service(String id, File workingdir) {
		this.isMaster = id.equals("services");
		this.workingdir = workingdir;
		this.id = id;
		commands.put("clearlog", new ClearLogCommand(this));
		commands.put("reloadconfig", new ReloadConfigCommand(this));
		this.updateFromConfig();
		
		// The "services" service (ie this program) is already running
		if (isMaster) {
			commands.put("reloadservicelist", new ReloadServiceListCommand(this));
			run = true;
			running = true;
			
		// For other services, start the start command
		} else {
			execCommand("start");
		}
	}
	public void updateFromConfig() {
		File settingsFile = new File(this.workingdir, Manager.getSetting("service_json", "service.json"));
		ServiceSettings settings;
		Map<String,String> serviceDirList;
		try {
			String json = Manager.readFile(new FileInputStream(settingsFile));
			Gson gson = new Gson();

			settings = gson.fromJson(json, ServiceSettings.class);
		} catch (FileNotFoundException e) {
			logErr("Can't find service settings file: ".concat(settingsFile.getAbsolutePath()));
			return;
		} catch (IOException e) {
			logErr("Can't read service settings file: ".concat(settingsFile.getAbsolutePath()));
			return;
		} catch (JsonSyntaxException e) {
			logErr("Invalid JSON in service settings file: ".concat(settingsFile.getAbsolutePath()));
			return;
		} catch (JsonParseException e) {
			logErr("Invalid format in service settings file: ".concat(settingsFile.getAbsolutePath()));
			return;
		}
		port = settings.getPort();
		name = settings.getName();
		
		Iterator iter = settings.getCommands().entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry header = (Map.Entry)iter.next();
			String cmd = (String)header.getValue();
			String name = (String)header.getKey();
			String key = name.toLowerCase();
			
			if (commands.containsKey(key)) {
				commands.get(key).update(cmd, name);
			} else {
				Command command = new Command(this, cmd, name);
				commands.put(key, command);
			}
		}
		
		if (name == null) logErr("Missing name in settings file: ".concat(settingsFile.getAbsolutePath()));
		
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
		if (name == null) return id;
		return name;
	}
	public File getWorkingDir() {
		return workingdir;
	}
	public void log(String line) {
		if (isMaster) System.out.println(line);
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
		if (isMaster) System.err.println(line);
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
		if (isMaster) {
			System.err.println(e);
			e.printStackTrace(System.err);
		}
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
		Manager.log("Starting service "+this.getName()+" ("+command+")");
	}
	void stop() {
		
		// Can't kill services module
		if (isMaster) return;
		if (!run) return;
		run = false;
		if (!running) return;
		currentProcess.destroy();
		Manager.log(this.getName() +" process destroyed");
	}
	void clearLog() {
		stdErr.clear();
		stdOut.clear();
	}
	public boolean execCommand(String key) {
		Command command = commands.get(key);
		if (command == null) return false;
		command.run = true;
		Thread thread = new Thread(command);
		thread.start();
		return true;
	}
	public boolean isRunning() {
		if (isMaster) return true;
		if (!commands.containsKey("start")) return false;
		return commands.get("start").isRunning();
	}
	public boolean hasError() {
		return (stdErr.size() > 0);
	}
	public String getId() {
		return id;
	}
	public Iterator getDataIterator() {
		Map<String, String> data =  new HashMap<String, String>();
		data.put("port", ""+port);
		data.put("path", workingdir.getAbsolutePath());
		if (command != null) data.put("command", command);
		data.put("name", this.getName());
		data.put("running", (this.isRunning())?"running":"stopped");
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
			String action = "/services/"+id+"/"+header.getKey();
			commandlist += "\t<li><form method='post' action=\""+action+"\">"
			+ "<input type='submit' value=\""
			+ command.getName().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
			+ "\" /></form></li>\n";
		}
		commandlist += "</ul>";
		data.put("commandlist_html", commandlist);
		
		
		return data.entrySet().iterator();
	}
	public static Service getById(String id) {
		return serviceList.get(id);
	}
	public static Iterator getAllDataIterator() {
		Map<String, String> data =  new HashMap<String, String>();
		String servicelist = "<ul id='servicelist'>\n";
		Iterator iter = serviceList.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry header = (Map.Entry)iter.next();
			Service service = (Service)header.getValue();
			servicelist += "\t<li class=\""
			+((service.isRunning())?"running":"stopped")
			+((service.hasError())?" error":"")
			+"\"><a href=\"/services/"+service.getId()+"\">"
			+ service.getName().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
			+ "</a></li>\n";
		}
		servicelist += "</ul>";
		data.put("servicelist_html", servicelist);
		return data.entrySet().iterator();
	}

	/**
	 * Loads the Service for the currently running Service (this is quite meta, but allows for easy logging etc)
	 *
	 * @returns Service
	 */
	public static Service loadServicesService() {
		//Service services = new Service (Manager.getPort(), "services", "nice -n 18 java -cp .:../lib/java/* Manager", "Services");
		Service services = new Service("services", new File("."));
		//services.addCommand("build", "./build.sh", "Rebuild");
		serviceList.put("services", services);
		return services;
	}

	/**
	 * Loads all the services in the service list
	 *
	 * @returns void
	 */
	public static void loadServiceList() {
		File serviceListFile = new File (Manager.getSetting("root_path", ""), Manager.getSetting("service_list", "service_list.json"));
		Map<String,String> serviceDirList;
		try {
			String json = Manager.readFile(new FileInputStream(serviceListFile));
			Type listType = new TypeToken<HashMap<String,String>>(){}.getType();
			Gson gson = new Gson();

			serviceDirList = gson.fromJson(json, listType);
		} catch (FileNotFoundException e) {
			Manager.logErr("Can't find service list file: ".concat(serviceListFile.getAbsolutePath()));
			return;
		} catch (IOException e) {
			Manager.logErr("Can't read service list file: ".concat(serviceListFile.getAbsolutePath()));
			return;
		} catch (JsonSyntaxException e) {
			Manager.logErr("Invalid JSON in service list file: ".concat(serviceListFile.getAbsolutePath()));
			return;
		} catch (JsonParseException e) {
			Manager.logErr("Invalid format in service list file (should be an object of key/value pairs): ".concat(serviceListFile.getAbsolutePath()));
			return;
		}

		for (Map.Entry<String,String> entry : serviceDirList.entrySet()) {
			String serviceKey = entry.getKey();
			if (serviceList.containsKey(serviceKey)) {
				serviceList.get(serviceKey).updateFromConfig();
			} else {
				File directory = new File(Manager.getSetting("root_path", ""), entry.getValue());
				Service service = new Service(serviceKey, directory);
				serviceList.put(serviceKey, service);
			}
		}
	}
	
	public static Service getAuth() {
		return serviceList.get("auth");
	}

	static class ServiceSettings {
		private int port;
		private Map<String, String> commands;
		private String name;
		public int getPort() {
			return port;
		}
		public Map<String, String> getCommands() {
			if (commands == null) return new HashMap<String, String>();
			return commands;
		}
		public String getName() {
			return name;
		}
	}
	
}
