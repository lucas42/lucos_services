import java.util.*;
import java.io.*;
abstract class TemplateValue {
	protected String type;
	abstract public String toString();
	public String getType() {
		return type;
	}
	
	/**
	 * Returns the output of the value escaped for use in a templae
	 * @param String escType The type of the template
	 * @returns String
	 */
	public String getEscaped(String escType) {
		String output = this.toString();
		if (output == null) {
			Manager.logErr("null toString() " + this.getClass());
		}
		if (escType.equals("html")) {
			
			// Any non-html values put into an html template should be html encoded
			if (escType.equals("html") && !this.getType().equals("html")) {
				output = output.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
			}
			
			// Escape any dollar signs in the values in case they get mixed up with placeholders
			output = output.replace("$", "\\$");
		}
		return output;
	}
}
public class Template extends TemplateValue{
	private static File templateDir = new File(Manager.getSetting("template_dir", ""));
	private File templateFile;
	private String content;
	private String type;
	private Map<String, TemplateValue> data;
	public Template(String name) throws IOException {
		this(name, "html");
	}
	public Template(String name, String type) throws IOException  {
		this.type = type;
		templateFile = new File(templateDir, name + "." + type);
		content = Manager.readFile(new FileInputStream(templateFile));
		data = new HashMap<String, TemplateValue>();
		if (type.equals("html")) setData("rootdomain", Service.getById("root").getDomain());
	}
	public void setData(String key, String val) {
		if (val != null) setData(key, new TemplateString(val));
	}
	public void setData(String key, TemplateValue val) {
		data.put(key, val);
	}
	public String getFileName() {
		return templateFile.getName();
	}
	public String getType() {
		return type;
	}
	public String toString() {
		String output = new String(content);
		Iterator<Map.Entry<String, TemplateValue>> iter = data.entrySet().iterator();
		
        while (iter.hasNext()) {
			Map.Entry<String, TemplateValue> entry = iter.next();
			String key = entry.getKey();
			TemplateValue val = entry.getValue();
			if (key == null || val == null) continue;
			key = "$"+key+"$";
			
			output = output.replace(key, val.getEscaped(type));
        }
		
		
		// Replace any placeholders which haven't got data with empty strings
		output  = output.replaceAll("\\$.+?\\$", "");
		
		// Unescape remaining dollar signs
		output = output.replace("\\$", "$");
		return output;
	}
}

/**
 * A very simple extention of TemplateValue, which returns a single string
 */
class TemplateString extends TemplateValue {
	private String value;
	public TemplateString(String value) {
		if (value == null) throw new NullPointerException();
		this.type = "text";
		this.value = value;
	}
	public String toString() {
		return value;
	}
}

class TemplateGroup extends TemplateValue {
	private List<TemplateValue> values;
	public TemplateGroup(String type) {
		this.type = type;
		values = new LinkedList<TemplateValue>();
	}
	public void add(TemplateValue val) {
		
		// Only add values of the same type to avoid confusion
		if (val.getType().equals(type)) values.add(val);
	}
	public String getEscaped(String escType) {
		String output = "";
		Iterator<TemplateValue> iter = values.iterator();
		while (iter.hasNext()) {
			TemplateValue val = iter.next();
			output += val.getEscaped(escType);
		}
		return output;
	}
	public String toString() {
		String output = "";
		Iterator<TemplateValue> iter = values.iterator();
		while (iter.hasNext()) {
			TemplateValue val = iter.next();
			output += val.toString();
		}
		return output;
	}
}