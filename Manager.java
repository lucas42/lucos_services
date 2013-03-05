import java.io.* ;
import java.net.* ;
import java.util.*;
public final class Manager {
	private static Properties settings = new Properties();
	private static Service services;
	public static void main(String argv[]) throws Exception {
		settings.load(Manager.class.getClassLoader().getResourceAsStream("config.properties"));
		
		
		setupServices();
		
		// Establish the listen socket.
		ServerSocket serverSocket = new ServerSocket(services.getPort());
    
		// Process HTTP service requests in an infinite loop.
		while (true) {
			// Listen for a TCP connection request.

			Socket clientSocket = serverSocket.accept();
			PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
			// Construct an object to process the HTTP request message.
			HttpRequest request = new HttpRequest( clientSocket );
			// Create a new thread to process the request.
			Thread thread = new Thread(request);
			// Start the thread.
			thread.start();

		}
		
	}
	public static String getSetting(String key) {
		return settings.getProperty(key);
	}
	public static String getSetting(String key, String defaultValue) {
		return settings.getProperty(key, defaultValue);
	}
	public static void log(String line) {
		if (services != null) services.log(line);
	}
	public static void logErr(String line) {
		if (services != null) services.logErr(line);
	}
	public static void logErr(Exception e) {
		if (services != null) services.logErr(e);
	}

	public static boolean authRunning() {
		Service auth = Service.getAuth();
		if (auth == null) return false;
		return auth.isRunning();
	}
	public static String authDomain() {
		Service auth = Service.getAuth();
		if (auth == null) throw new RuntimeException("Can't get auth domain as auth service isn't running");
		return "auth.l42.eu";
		//return "localhost:" +  auth.getPort();
	}
	public static String readFile(FileInputStream fis) throws IOException {
		
		int bytes = 0;
		StringBuffer contentBuffer = new StringBuffer("");
		while((bytes = fis.read()) != -1)
		    contentBuffer.append((char)bytes);
		fis.close();

		return contentBuffer.toString();
	}
	
	/**
	 * Creates all the services
	 */
	public static void setupServices() {
		
		// Make sure this service is created first, for logging purposes
		/*services = new Service (getPort(), "services", "nice -n 18 java -cp .:../lib/java/* Manager", "Services");
		services.addCommand("build", "./build.sh", "Rebuild");*/
		services = Service.loadServicesService();
		Service.loadServiceList();
		/*Service mediamanager = new Service(8001, "media/manager", "java -cp .:../../lib/java/* Manager", "Media Manager");
		mediamanager.addCommand("build", "./build.sh", "Rebuild");
		Service mediaselector = new Service(8002, "media/selector", "./playlist.pl", "Media Selector");
		mediaselector.addCommand("weighting", "../weighting/weighting_cron", "Recalculate Weightings");
		Service root = new Service(8003, "root", "java -cp .:../lib/java/* Server", "Root");
		root.addCommand("build", "./build.sh", "Rebuild");
		Service notes = new Service(8004, "notes", "node server.js", "Notes");		
		Service contacts = new Service(8005, "contacts/contacts", "python manage.py runserver 0.0.0.0:8005 --noreload", "Contacts");
		auth = new Service(8006, "auth", "node server.js", "Authentication");
		Service status = new Service(8007, "status", "node server.js", "Status");
		Service time = new Service(8008, "time", "node server.js", "Am");
		Service travel = new Service(8009, "travel", "./server.rb", "Travel");
		Service googlecontactssync = new Service(8011, "contacts/googlesync", "./server.rb", "Google Contacts sync");
		Service dnsupdater = new Service(8012, "dns", "./server.pl", "DNS Updater");
		Service progs = new Service(8013, "progs", "./server.py", "Progs v4");
		Service speak = new Service(8014, "speak", "node server.js", "Speak");*/
	}
}
