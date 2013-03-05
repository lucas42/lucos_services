import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
public class Service {
	
	// The tcp port HTTP requests are sent to for this service
	private int port;
	
	private File workingdir;
	
	private String name;
	private String id;
	
	private Queue<String> stdOut = new LinkedList<String>();
	private Queue<String> stdErr = new LinkedList<String>();
	
	// Whether the service refers to this program
	private final boolean isMaster;
	
	// A list of the commands which can be run for this service
	private Map<String, Command> commands = new HashMap<String, Command>();
	
	// A list of all the services
	private static Map<String, Service> serviceList =  new HashMap<String, Service>();
	
	
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
		// For other services, start the start command
		} else {
			commands.put("start", new StartCommand(this));
			commands.put("stop", new StopCommand(this));
			commands.put("restart", new RestartCommand(this));
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
		
		// TODO: remove any commands which have been removed from JSON
		
		if (name == null) logErr("Missing name in settings file: ".concat(settingsFile.getAbsolutePath()));
		
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
	void clearLog() {
		stdErr.clear();
		stdOut.clear();
	}
	public boolean execCommand(String key) {
		return execCommand(key, false);
	}
	public boolean execCommand(String key, boolean keepRunning) {
		Command command = commands.get(key);
		if (command == null) return false;
		command.exec(keepRunning);
		return true;
	}
	public void stopCommand(String key) {
		Command command = commands.get(key);
		if (command == null) return;
		command.kill();
	}
	public boolean isRunning() {
		if (isMaster) return true;
		if (!commands.containsKey("main")) return false;
		return commands.get("main").isRunning();
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
			
			// Ignore the main command, as it's wrapped by Start/Stop/Restart
			if (header.getKey().equals("main")) continue;
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
		Service services = new Service("services", new File("."));
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
