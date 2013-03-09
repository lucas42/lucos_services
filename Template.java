import java.util.*;
import java.io.*;
public class Template {
	private static File templateDir = new File(Manager.getSetting("template_dir", ""));
	private File templateFile;
	private String content;
	private String type;
	public Template(String name) throws IOException {
		this(name, "html");
	}
	public Template(String name, String type) throws IOException  {
		this.type = type;
		templateFile = new File(templateDir, name + "." + type);
		content = Manager.readFile(new FileInputStream(templateFile));
			
	}
	public void setData(Map<String, String> data) {
		Iterator<Map.Entry<String, String>> iter = data.entrySet().iterator();
		
		
        while (iter.hasNext()) {
			Map.Entry<String, String> keyval = iter.next();
			String key = keyval.getKey();
            String val = keyval.getValue();
			if (val == null) {
				Manager.logErr("Null value found for key '"+key+"' in template.setData()");
				continue;
			}
			setData(key, val);
        }
		
	}
	public void setData(String key, String val) {
		key = "$"+key+"$";
		
		// When the template type is html, default to html encoding values unless they end in '_html'
		if (type.equals("html")) {
			if (key.endsWith("_html$")) key = key.replace("_html", "");
			else val = val.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
		}
		
		// Escape any dollar signs in the values in case they get mixed up with placeholders
		val = val.replace("$", "\\$");
		content = content.replace(key, val);
		
	}
	public String getFileName() {
		return templateFile.getName();
	}
	public String toString() {
		String output = new String(content);
		
		// Replace any placeholders which haven't got data with empty strings
		output  = output.replaceAll("/$.+?$/", "");
		
		// Unescape remaining dollar signs
		output = output.replace("\\$", "$");
		return output;
	}
}