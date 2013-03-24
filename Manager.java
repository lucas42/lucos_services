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
		try {
			return Service.getById("auth").isRunning();
		} catch (RuntimeException e) {
			return false;
		}
	}
	public static String authDomain() {
		return Service.getById("auth").getDomain();
	}
	public static String servicesDomain() {
		return services.getDomain();
	}
	public static String readFile(FileInputStream fis) throws IOException {
		
		int bytes = 0;
		StringBuffer contentBuffer = new StringBuffer("");
		while((bytes = fis.read()) != -1)
		    contentBuffer.append((char)bytes);
		fis.close();

		return contentBuffer.toString();
	}
	public static void updateVarnish() {
		services.execCommand("updatevarnish");
	}
	
	/**
	 * Creates all the services
	 */
	public static void setupServices() {
		
		// Make sure the services service is created first, for logging purposes
		services = Service.loadServicesService();
		Service.loadServiceList();
	}
}
