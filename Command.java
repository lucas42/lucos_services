import java.util.* ;
import java.io.*;
class Command implements Runnable {
	protected Service service;
	private String cmd;
	protected String name;
	private boolean run = false;  // Whether the command should be running
	private boolean running = false; // Whether the command is running
	private boolean isMain;
	private Process currentProcess;
	public Command(Service service, String cmd, String name) {
		this.service = service;
		this.cmd = cmd;
		
		// The 'main' command should inherit its name from the service
		if (name.equals("main")) {
			this.name = service.getName();
			isMain = true;
		} else {
			this.name = name;
			isMain = false;
		}
	}
	public void run() {
		
		// Don't let each command run more than once concurrently
		if (isRunning()) return;
		do {
			
			// Pass port and domain of this service to the command if neccessary.
			String populatedcmd = cmd.replace("%p", Integer.toString(service.getPort())).replace("%d", Manager.servicesDomain());
			try {
				final Process process = Runtime.getRuntime().exec(populatedcmd, null, service.getWorkingDir());
				running = true;
				currentProcess = process;
				
				final BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
				final BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				try {
					
					
					// Destroy the command's process on shutdown
					Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
						public void run() {
							service.log("Stopping command "+name);
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
				} catch (IllegalStateException e) {
					
					// This occurs when the JVM is shutting down, in which case don't bother trying to do anything afterwards
					return;
				} catch (InterruptedException e) {
					service.logErr("Command "+name+" interrupted");
					service.logErr(e);
				}
				running = false;
				
				// Tidy up the old process and any pipes left open before doing anything else
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
			} catch (IOException e) {
				service.logErr("Process "+name+" didn't load due to IOException");
				service.logErr(e);
			}
			if (run) {
				if (isMain) Manager.log("Service "+name+" stopped, restarting in 30 seconds...");
				try {
					Thread.sleep(30*1000);
				} catch (InterruptedException e) {
					Manager.logErr("Sleep interrupted, going straight to restart...");
					Manager.logErr(e);
				}
			}
		} while (run);
	}
	public String getName() {
		return name;
	}
	public boolean isRunning() {
		return running;
	}
	public void runOnce() {
		exec(false);
	}
	public void keepRunning() {
		exec(true);
	}
	public void exec(boolean keepRunning) {
		run = keepRunning;
		Thread thread = new Thread(this);
		thread.start();
	}
	public void kill() {
		run = false;
		if (!isRunning()) return;
		currentProcess.destroy();
		Manager.log("Process "+name+" killed");
	}
	
	/**
	 * Updates the command and name.  Restarts if the command has changed
	 *
	 * @return void
	 */
	public void update(String cmd, String name) {
		boolean needsRestart = !cmd.equals(this.cmd);
		this.cmd = cmd;
		this.name = name;
		if (needsRestart) {
			service.logErr("Restart not yet implemented");
		}
	}
}

class StartCommand extends Command {
	public StartCommand(Service service) {
		super(service, null, "Start");
	}
	public void run() {
		service.execCommand("main", true);
	}
}

class StopCommand extends Command {
	public StopCommand(Service service) {
		super(service, null, "Stop");
	}
	public void run() {
		service.stopCommand("main");
	}
}

class RestartCommand extends Command {
	public RestartCommand(Service service) {
		super(service, null, "Restart");
	}
	public void run() {
		service.stopCommand("main");
		service.execCommand("main", true);
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

class ReloadConfigCommand extends Command {
	public ReloadConfigCommand(Service service) {
		super(service, null, "Reload Config");
	}
	public void run() {
		service.updateFromConfig();
		service.log("Updated Service from Config");
		Manager.updateVarnish();
	}
}

class ReloadServiceListCommand extends Command {
	public ReloadServiceListCommand(Service service) {
		super(service, null, "Reload Service List");
	}
	public void run() {
		Service.loadServiceList();
		service.log("Loaded Service List");
	}
}

class UpdateVarnishCommand extends Command {
	public UpdateVarnishCommand(Service service) {
		super(service, "sudo /usr/sbin/service varnish reload", "Update Varnish");
	}
	public void run() {
		try {
			Template vcl = Service.getVCL();
			PrintWriter output = new PrintWriter(Manager.getSetting("vcl_path", "services.vcl"));
			output.println(vcl.toString());
			output.close();
		} catch (FileNotFoundException e) {
			Manager.logErr(e);
		}
		super.run();
	}
}
