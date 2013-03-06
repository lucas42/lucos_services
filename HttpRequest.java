import java.io.* ;
import java.net.* ;
import java.util.* ; 
import com.google.gson.*;

final class HttpRequest implements Runnable {
    final static String CRLF = "\r\n";
    Socket socket;
    DataOutputStream os;
    OutputStreamWriter osw;
    Map<String, String> header = new HashMap<String, String>();
    private Queue<String> request = new LinkedList<String>();
    static Map<String, Integer> agents = new HashMap<String, Integer>();
    
    // Constructor
    public HttpRequest(Socket socket) throws Exception {
        this.socket = socket;
    }
    
    // Implement the run() method of the Runnable interface.
    public void run() {
        processRequest();
    }
    
    private void processRequest() {
        try {
        
            // Get a reference to the socket's input and output streams.
            final InputStream is = new DataInputStream(socket.getInputStream());
            final DataOutputStream os = new DataOutputStream(socket.getOutputStream());
            this.os = os;
            osw = new OutputStreamWriter(os, "UTF8");
            final BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF8"));
            final Socket socket = this.socket;

            try {
                    
                // Get the header lines.
                String headerLine;
                String host = null;
                String cookiestr = null;
                int contentlength = -1;
                boolean headers = true;
                while ((headerLine = br.readLine()) != null) {
                    if (headerLine.length() < 1) break;
                     
					request.add(headerLine);
                    int jj = headerLine.indexOf(':');
                    if (jj == -1) continue;
                    String field = headerLine.substring(0, jj).trim();
                    String value = headerLine.substring(jj+1).trim();
                    if (field.equals("Host")) {
                        host = value;
                    }
                    if (field.equals("Cookie")) {
                        cookiestr = value;
                    }
                    if (field.equals("Content-Length")) {
                        try {
                            contentlength = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                        }
                    }
                }
                if (host == null) {
                    Manager.log("Empty Host");
                    return;
                }
                request.add(CRLF);

                String requestLine = request.peek();
                // Extract the filename from the request line.
                StringTokenizer tokens = new StringTokenizer(requestLine);

                String method = tokens.nextToken().trim();
                String path = tokens.nextToken().trim();
                Map<String, String> get = new HashMap<String, String>();
                        int ii = path.indexOf('?');
                        if (ii > -1) {
                                String[] getstring = path.substring(ii+1).split("&");
                                for (String key : getstring) {
                                        int jj = key.indexOf('=');
                                        String field;
                                        if ( jj > -1) {
                                            field = key.substring(jj+1);
                                            key = key.substring(0, jj);
                                        } else {
                                                field = "true";
                                        }
                                        key = URLDecoder.decode(key, "UTF-8");
                                        field = URLDecoder.decode(field, "UTF-8");
                                        get.put(key, field);
                                }
                                path = path.substring(0, ii);
                        }

                                    Map<String, String> cookies = new HashMap<String, String>();
                                    if (cookiestr != null) {
                    String[] cookiestrs = cookiestr.split(";");
                                            for (String key : cookiestrs) {
                                                    int jj = key.indexOf('=');
                                                    String field;
                                                    if ( jj > -1) {
                                                        field = key.substring(jj+1);
                                                        key = key.substring(0, jj);
                                                    } else {
                                                            field = "true";
                                                    }
                                                    key = URLDecoder.decode(key, "UTF-8").trim();
                                                    field = URLDecoder.decode(field, "UTF-8").trim();
                                                    cookies.put(key, field);
                                            }
                                    }

                                    String[] pathParts = path.split("\\/");
                String fileName;
                if (path.equals("/icon")) path = "/icon.png";
                fileName = "./data" + path;
                fileName.replaceAll("/\\.\\./","");
                Service service = null;
                String setCookie = null;
                if (pathParts.length > 2 && pathParts[1].equals("services")) {
                    String id = pathParts[2];
                    if (id.length() > 0) {
						service = Service.getById(id);
						if (service != null) {
							fileName = "./data/services/template.xhtml";
						}
                    }
                }
                if (fileName.charAt(fileName.length()-1) == '/') fileName += "index";
                if (!fileName.substring(1).contains(".")) fileName += ".xhtml";
                String token = get.get("token");
                if (token == null) token = cookies.get("token");
                
                // An agentid of null means the user hasn't authenticated - an agentid of zero indicates a problem retrieving the agentid from the authentication service
                Integer agentid = null;
                if (token != null) {
                    agentid = agents.get(token);
                    if (agentid == null && Manager.authRunning()) {
                        try{
                            URL dataurl = new URL("http://"+Manager.authDomain()+"/data?token="+URLEncoder.encode(token, "utf8"));
                            Gson gson = new Gson();
                            BufferedReader datain = new BufferedReader(new InputStreamReader(dataurl.openStream()));
                            String data = "";
                            String datastr;
                            while ((datastr = datain.readLine()) != null) {
                                data += datastr;
                            }
                            datain.close();
                            AuthData ad = gson.fromJson(data, AuthData.class);
                            agentid = ad.getId();
                            if (agentid > 0) {
                                agents.put(token, agentid);
                                setCookie = "token=" + URLEncoder.encode(token, "utf8");
                            }
                        } catch (FileNotFoundException e) {
                        } catch (IOException e) {
                        }
                    }
                }
                
                if (service != null && !isAuthorised(agentid, method, "http://"+host+path)) {
                    // Don't allow anything to happen without authorisation (the isAuthorised function should have sorted out the appropriate headers)
                } else if (service != null && pathParts.length > 3  && method.equalsIgnoreCase("POST") && service.execCommand(pathParts[3]))  {
                    redirect("/services/"+service.getId());
                } else if (path.equals("/") || path.equals("/services")) {
                    redirect("/services/");
                } else {
                    // Open the requested file.
                    FileInputStream fis = null;
                    boolean fileExists = true;
                    String statusLine = null;
                    try {
                        fis = new FileInputStream(fileName);
                         statusLine = "HTTP/1.1 200 OK";
                    } catch (FileNotFoundException e) {
                        Manager.log("File Not found: "+fileName);
                        fileName = "./data/404.html";
                        statusLine = "HTTP/1.1 404 File Not Found";
                        try {
                            fis = new FileInputStream(fileName);
                        } catch (FileNotFoundException e2) {
                            fileExists = false;
                        }
                    }



                    // Construct the response message.
                    String contentTypeLine = null;
                    String entityBody = null;
                    if (fileExists) {
                        contentTypeLine = "Content-Type: " +
                            contentType( fileName ) + "; charset=UTF-8";
                    }
                    // Send the status line.
                    os.writeBytes(statusLine + CRLF);
                    // Send the content type line.
                    if (contentTypeLine != null) os.writeBytes(contentTypeLine + CRLF);
                                            // Send the setcookie line.
                                            if (setCookie != null) os.writeBytes("Set-Cookie: " + setCookie + CRLF);

                    // Send a blank line to indicate the end of the header lines.
                    os.writeBytes(CRLF);
                    
                    
                    if (fileExists) {
                        if (service != null) {
                            html(fis, service.getDataIterator());
                        } else if (path.equals("/services/")) {
                            html(fis, Service.getAllDataIterator());
                        } else {
                            // Construct a 1K buffer to hold bytes on their way to the socket.
                            byte[] buffer = new byte[1024];
                            int bytes = 0;
                           
                            // Copy requested file into the socket's output stream.
                            while((bytes = fis.read(buffer)) != -1 ) {
                                os.write(buffer, 0, bytes);
                            }
                            fis.close();
                        }
                    } else {
                         os.writeBytes("error: 404 file not found");
                    }
                }
                
                // Close streams and socket.
                osw.close();
                br.close();
                socket.close();
            
				
			} catch (SocketException e) {
				// Don't do anything if there's a socketexception - it's probably just the client disconnecting before it's received the full request
            } catch (Exception e) {
                Manager.logErr("Server Error (HttpRequest):");
                Manager.logErr(e);
            }
            
        } catch (IOException e) {
            Manager.logErr("Server Error (HttpRequest):");
            Manager.logErr(e);
            
        }
    }
    private static String contentType(String fileName) {
        if(fileName.endsWith(".htm") || fileName.endsWith(".html")) {
            return "text/html";
        }
        if(fileName.endsWith(".xhtml")) {
            return "application/xhtml+xml";
        }
        if(fileName.endsWith(".png")) {
            return "image/png";
        }
        if(fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if(fileName.endsWith(".jpg")) {
            return "image/jpeg";
        }
        if(fileName.endsWith(".css")) {
            return "text/css";
        }
        if(fileName.endsWith(".js")) {
            return "text/javascript";
        }
        if(fileName.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if(fileName.endsWith("manifest")) {
            return "text/cache-manifest";
        }
        return "application/octet-stream";
    }
    private void sendHeaders(int status, String statusstring, Map<String, String> extraheaders) throws IOException {
        os.writeBytes("HTTP/1.1 "+ status +" "+ statusstring + CRLF);
        os.writeBytes("Access-Control-Allow-Origin: *" + CRLF);
        os.writeBytes("Server: lucos" + CRLF);
        Iterator iter = extraheaders.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry header = (Map.Entry)iter.next();
            os.writeBytes(header.getKey()+": "+header.getValue() + CRLF);
        }
        os.writeBytes(CRLF);
    }
    private void sendHeaders(int status, String statusstring, String contentType) throws IOException {
        HashMap<String, String> headers =  new HashMap<String, String>();
        headers.put("Content-type", contentType+ "; charset=utf-8");
        sendHeaders(status, statusstring, headers);
    }
    private void redirect(String url) throws IOException {
        HashMap<String, String> headers =  new HashMap<String, String>();
        headers.put("Location", url);
        sendHeaders(302, "Redirect", headers);
    }
    private void html(FileInputStream fis, Iterator dataIter) throws IOException {
        String content = Manager.readFile(fis);

        while (dataIter.hasNext()) {
            Map.Entry keyval = (Map.Entry)dataIter.next();
            String key = "$"+keyval.getKey()+"$";
            String val = (String)keyval.getValue();
			if (val == null) {
				val = "";
				Manager.logErr("Null value found for key '"+key+"' in html()");
			}
            if (key.endsWith("_html$")) key = key.replace("_html", "");
            else val = val.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
			
			// Replace any dollars in the values in case they get mixed up with placeholders
			val = val.replace("$", "&#36;");
            content = content.replace(key, val);
        }
		os.writeBytes(content);
        
    }
    private boolean isAuthorised(Integer agentid, String method, String uri) throws Exception {
        
        // Luke is authorised
        if (agentid != null && agentid.intValue() == 2) return true;
            
        // If the auth service is running then make sure the user has authenticated
        if (Manager.authRunning() && agentid == null) {
            redirect("http://"+Manager.authDomain()+"/authenticate?redirect_uri="+URLEncoder.encode(uri, "utf8"));
            return false;
        }
        
        // If the user has successfully authenticated, but isn't authorised, return a 403
        if (agentid != null && agentid > 0) {
            sendHeaders(403, "Permission Denied", "text/plain");
            return false;
        }
        
        /* Ideally never go past this point - this means either the authentication server isn't running or has returned an invalid agentid */
        if (!Manager.authRunning()) Manager.logErr("Auth service isn't running, using fallback auth rules");
        else Manager.logErr("Auth service returned invalid agentid, using fallback auth rules");
            
        // Allow GET requests so that whatever is causing the problem can be debugged
        if (method.equalsIgnoreCase("GET")) return true;
        
        // Don't allow any other requests as the user hasn't been authenticated
        sendHeaders(403, "Authentication Error", "text/plain");
        return false;
    }

    static class AuthData {
        private int id;
        public AuthData() {
        }
        public int getId() {
             return id;
        }
    }

}
