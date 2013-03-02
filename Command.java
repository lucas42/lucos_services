import java.util.* ;
import java.io.*;
class Command implements Runnable {
	protected Service service;
	private String cmd;
	protected String name;
	public Command(Service service, String cmd, String name) {
		this.service = service;
		this.cmd = cmd;
		this.name = name;
	}
	public void run() {
		try {
			final Process process = Runtime.getRuntime().exec(cmd, null, service.getWorkingDir());
			
			
			final BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
			final BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			
			new Thread(new Runnable() {  
				public void run() {
					try {
						String s = null;
						while ((s = stdInput.readLine()) != null) {
							service.log(s);
						}
					} catch (IOException e) {
						service.logErr("Can't read from "+name+", IOException.");
						service.logErr(e);
					}
				}
			}).start();
			
			new Thread(new Runnable() {  
				public void run() {
					try {
						String s = null;
						while ((s = stdError.readLine()) != null) {
							service.logErr(s);
						}
					} catch (IOException e) {
						service.logErr("Can't read err from "+name+", IOException.");
						service.logErr(e);
					}
				}
			}).start();
			
			
			process.waitFor();
			service.log("Command "+name+" completed");
		} catch (InterruptedException e) {
			service.logErr("Command "+name+" interrupted");
			service.logErr(e);
		} catch (IOException e) {
			service.logErr("Command "+name+" died of IOException");
			service.logErr(e);
		}
	}
	public String getName() {
		return name;
	}
}

class StartCommand extends Command {
	public StartCommand(Service service) {
		super(service, null, "Start");
	}
	public void run() {
		service.start();
	}
}

class StopCommand extends Command {
	public StopCommand(Service service) {
		super(service, null, "Stop");
	}
	public void run() {
		service.stop();
	}
}

class RestartCommand extends Command {
	public RestartCommand(Service service) {
		super(service, null, "Restart");
	}
	public void run() {
		service.stop();
		service.start();
	}
}

class ClearLogCommand extends Command {
	public ClearLogCommand(Service service) {
		super(service, null, "Clear Log");
	}
	public void run() {
		service.clearLog();
	}
}
