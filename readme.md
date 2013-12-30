#lucos Services
_A java program which will run other lucos services_

##Configuration


###config.properties
_This should be placed in the root directory of this project (i.e. the same level as README.md)_

These are config options used for all services being run.  Options include:
* **root_path**: The path of the root lucos directory
* **output_length**: The number of lines of stdout and stderr to display for each service
* **root_domain**: The domain to append to each of the services' subdomains
* **template_dir**: The path of the templates directory (relative to the services project root)
* **service_json**: The filename of each service's config file (defaults to service.json)  See below for delaits on this file.
* **service_list**: The filename of the list of which services to run, relative to **root_path** (defaults to service_list.json) See below for delaits on this file.
* **vcl_path**: The path of the varnish config file which the project can update


###service_list.json
There should be a single list of which services to run stored in **root_path**.  This should contain a json object where the keys are identifiers of each service and the value is the path of the project, relative to **root_path**

###service.json
Each lucos service should have a config file in the root of its project.  This config file is json encoded and can include the following options:
* **name**: ( *String* ) The name of the service
* **port**: ( *int* ) The port the service runs on
* **sudomain**: ( *String* ) The subdomain to run the service on
* **disablecaching**: ( *boolean* ) Whether to disable all caching of this service in Varnish
* **combinestdouterr**: ( *boolean* ) Whether to treat output from stderr as if it were from stdout
* **commands**: ( *object* ) A list of key/value pairs where the key is a human-readable label for the command and the value is the command itself.  The service's primary command (which will be automatically restarted if it fails), should have a key of *main*.


##Dependencies
* Java
* [Google gson](https://code.google.com/p/google-gson/)
* [Varnish](https://www.varnish-cache.org/)

##Installation
To build the project, run *./build.sh*
To run the project, run *./run*
The user which runs the project should have the following permissions:
* Permission to listen to a TCP socket
* Permission to edit the file which vcl_path points to
* Permission to reload varnish
* Permission to run each of the services listed in services_list.json (and any of their subcommands)
