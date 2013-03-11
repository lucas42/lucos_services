A java program which will run other lucos services

Configuration:
service.json
Each lucos service should have a config file in the root of its project.  This config file is json encoded and can include the following options:
* name: (String) The name of the service
* port: (int) The port the service runs on
* sudomain: (String) The subdomain to run the service on
* disablecaching: (boolean) Whether to disable all caching of this service in Varnish
* commands: (object) A list of key/value pairs where the key is a human-readable label for the command and the value is the command itself.  The service's main command (which will be automatically restarted if it fails), should have a key of 'main'

service_list.json
There should be a single list of which services to run stored in the root lucos directory.  This should contain a json object where the keys are identifiers of each service and the value is the path of the project, relative to the root lucos directory

config.properties
These are config options used for all services being run.  Options include:
* root_path: The path of the root lucos directory
* output_length: The number of lines of stdout and stderr to display for each service
* root_domain: The domain to append to each of the services' subdomains
* template_dir: The path of the templates directory (relative to the services project root)
* service_list: The path of the service list (relative to the root lucos directory)
* service_json: The filename of each service's config file
* vcl_path: The path of the varnish config file which the project can update

Dependancies:
* Java
* Varnish

Installation:
To build the project, run ./build.sh
To run the project, run ./run
The user which runs the project should have the following permissions:
* Permission to listen to a TCP socket
* Permission to edit the file which vcl_path points to
* Permission to reload varnish
* Permission to run each of the services listed in services_list.json (and any of their subcommands)
