package net.scientifichooliganism.xmlplugin;

import java.io.File;

import java.util.Collection;
import java.util.Vector;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import net.scientifichooliganism.javaplug.interfaces.Plugin;
import net.scientifichooliganism.javaplug.interfaces.Store;

/**
* This class does stuff, maybe.
*/
public class XMLDataStorePlugin implements Plugin, Store {
	private static XMLDataStorePlugin instance;
	private Vector<String> resources;

	private XMLDataStorePlugin() {
		resources = new Vector<String>();
	}

	public static XMLDataStorePlugin getInstance() {
		if (instance == null) {
			instance = new XMLDataStorePlugin();
		}

		return instance;
	}

	public void validateQuery (String query) throws IllegalArgumentException {
		if (query == null) {
			throw new IllegalArgumentException("validateQuery (String) ");
		}

		if ((query.length() <= 0) || (query.toLowerCase().matches("^\\s*$"))) {
			throw new IllegalArgumentException("validateQuery (String) ");
		}
	}

	public Collection query (String query) throws IllegalArgumentException {
		System.out.println("XMLDataStorePlugin.query(String)");
		query = query.trim().toLowerCase();
		validateQuery(query);
		Vector results = new Vector();

		for (String resource: resources) {
			try {
				File resourceFile = new File(resource);

				if (resourceFile.isFile()) {
					String resourceExtension = resourceFile.getCanonicalPath();
					resourceExtension = resourceExtension.substring(resourceExtension.lastIndexOf("."));
					resourceExtension = resourceExtension.trim().toLowerCase();

					if (resourceExtension.equals("xml")) {
						System.out.println("	resource: " + resource);
					}
				}
			}
			catch (Exception exc) {
				exc.printStackTrace();
			}
		}

		return results;
	}

	public void persist (Object in) throws IllegalArgumentException {
		//
	}

	/**a bunch of tests, I mean, a main method*/
	public static void main (String [] args) {
		try {
			//dl.query(null);
			//dl.query("");
			//dl.query(" ");
		}
		catch (Exception exc) {
			exc.printStackTrace();
		}
	}

	public String[][] getActions() {
		String actions[][] = new String [1][3];
		//actions[0] = {"XMLDataStorePlugin", "net.scientifichooliganism.xmlplugin", "printMessage"};
		return actions;
	}

	//override
	public boolean isStore() {
		return true;
	}

	public void addResource (Object in) throws IllegalArgumentException {
		addResource((String)in);
	}

	public void addResource (String resource) throws IllegalArgumentException {
		if (resource == null) {
			throw new IllegalArgumentException("addResource(String) was called with a null string");
		}

		if (resource.length() <= 0) {
			throw new IllegalArgumentException("addResource(String) was called with an empty string");
		}

		if (resources.contains(resource)) {
			throw new IllegalArgumentException("addResource(String) resources already contains an object with the value passed");
		}

		//System.out.println("adding resource: " + resource);
		resources.add(resource);
	}

	public Collection getResources () {
		return resources;
	}

	public void removeResource (Object in) throws IllegalArgumentException {
		removeResource((String)in);
	}

	public void removeResource (String resource) throws IllegalArgumentException {
		if (resource == null) {
			throw new IllegalArgumentException("addResource(String) was called with a null string");
		}

		if (resource.length() <= 0) {
			throw new IllegalArgumentException("addResource(String) was called with an empty string");
		}

		if (resources.contains(resource)) {
			resources.remove(resource);
		}
	}
}