import java.io.* ;
import java.net.* ;
import java.util.*;
public final class Manager {
	private static Properties settings = new Properties();
	private static int port;
	private static Service services;
	private static Service auth;
	public static void main(String argv[]) throws Exception {
		settings.load(Manager.class.getClassLoader().getResourceAsStream("config.properties"));
		
		
		
		// Set the port number.
		try{
			port = Integer.parseInt(getSetting("port", "8080"));
		} catch (NumberFormatException e) {
			port = 8080;
		}
		
		setupServices();
		
		// Establish the listen socket.
		ServerSocket serverSocket = new ServerSocket(port);
    
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
	public static int getPort() {
		return port;
	}
	public static void log(String line) {
		System.out.println(line);
		services.log(line);
	}
	public static void logErr(String line) {
		System.err.println(line);
		services.logErr(line);
	}
	public static void logErr(Exception e) {
		System.err.println(e);
		e.printStackTrace(System.err);
		services.logErr(e);
	}

	public static boolean authRunning() {
		return auth.isRunning();
	}
	
	/**
	 * Creates all the services
	 */
	public static void setupServices() {
		
		// Make sure this service is created first, for logging purposes
		services = new Service (getPort(), "services", "nice -n 18 java -cp .:../lib/java/* Manager", "Services");
		services.registerHost("services.l42.eu");
		services.addCommand("build", "./build.sh", "Rebuild");
		Service mediamanager = new Service(8001, "media/manager", "java -cp .:../../lib/java/* Manager", "Media Manager");
		mediamanager.addCommand("build", "./build.sh", "Rebuild");
		mediamanager.registerHost("ceol.l42.eu");
		Service mediaselector = new Service(8002, "media/selector", "./playlist.pl", "Media Selector");
		mediaselector.registerHost("media.l42.eu");
		mediaselector.addCommand("weighting", "../weighting/weighting_cron", "Recalculate Weightings");
		Service root = new Service(8003, "root", "java -cp .:../lib/java/* Server", "Root");
		root.addCommand("build", "./build.sh", "Rebuild");
		root.registerHost("l42.eu");
		Service notes = new Service(8004, "notes", "node server.js", "Notes");
		notes.registerHost("notes.l42.eu");
		Service contacts = new Service(8005, "contacts/contacts", "python manage.py runserver 0.0.0.0:8005 --noreload", "Contacts");
		contacts.registerHost("contacts.l42.eu");
		auth = new Service(8006, "auth", "node server.js", "Authentication");
		auth.registerHost("auth.l42.eu");
		Service status = new Service(8007, "status", "node server.js", "Status");
		status.registerHost("status.l42.eu");
		Service time = new Service(8008, "time", "node server.js", "Am");
		time.registerHost("am.l42.eu");
		Service travel = new Service(8009, "travel", "./server.rb", "Travel");
		travel.registerHost("travel.l42.eu");
		Service googlecontactssync = new Service(8011, "contacts/googlesync", "./server.rb", "Google Contacts sync");
		googlecontactssync.registerHost("googlecontactssync.l42.eu");
		Service dnsupdater = new Service(8012, "dns", "./server.pl", "DNS Updater");
		dnsupdater.registerHost("ns1.lukeblaney.co.uk");
		Service progs = new Service(8013, "progs", "./server.py", "Progs v4");
		progs.registerHost("progs2.l42.eu");
		Service speak = new Service(8014, "speak", "node server.js", "Speak");
		speak.registerHost("speak.l42.eu");
	}
}
