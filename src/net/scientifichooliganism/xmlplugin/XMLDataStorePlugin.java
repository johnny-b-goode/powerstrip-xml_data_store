package net.scientifichooliganism.xmlplugin;

import java.io.File;

import java.util.Collection;
import java.util.Vector;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import net.scientifichooliganism.javaplug.interfaces.Plugin;
import net.scientifichooliganism.javaplug.interfaces.Store;
import net.scientifichooliganism.javaplug.vo.*;

import net.scientifichooliganism.xmlplugin.bindings.*;

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
		System.out.println("XMLDataStorePlugin.validateQuery(String)");
		if (query == null) {
			throw new IllegalArgumentException("validateQuery(String) 1");
		}

		if ((query.length() <= 0) || (query.toLowerCase().matches("^\\s*$"))) {
			throw new IllegalArgumentException("validateQuery(String) 2");
		}

		query = query.toLowerCase();

		if (! (query.matches("^select.*"))) {
			throw new RuntimeException("validateQuery(String) query must begin with \"select\"");
		}

		if (! query.matches(".*[^'\"]from[^'\"]*.*")) {
			throw new RuntimeException("validateQuery(String) query must contain a \"from\" expression");
		}

		if ((query.matches(".*[^'\"]where[^'\"].*")) && ! (query.matches(".*[^'\"]where[^'\"][^=]*=.*"))) {
			throw new RuntimeException("validateQuery String) \"where\" must be followed by a condition");
		}

		System.out.println("	query successfully validated: " + query);
	}

	private String parseQuery (String strQuery) throws IllegalArgumentException, RuntimeException {
		System.out.println("XMLDataStorePlugin.parseQuery(String)");
		validateQuery(strQuery);
		String ret = null;
		strQuery = strQuery.toLowerCase();
		String queryBase = strQuery.substring(strQuery.indexOf("select")).trim();
		//yeah, yeah, I'm trying not to write a real parser here
		queryBase = queryBase.substring(6, strQuery.indexOf("from")).trim();
		String queryFrom = strQuery.substring(strQuery.indexOf("from")).trim();
		queryFrom = queryFrom.substring(4).trim();
		String queryWhere = null;

		if (queryFrom.contains("where")) {
			queryWhere = queryFrom.substring(queryFrom.indexOf("where")).trim();
			queryWhere = queryWhere.substring(5).trim();
			queryFrom = queryFrom.substring(0, queryFrom.indexOf("where")).trim();
		}

		System.out.println("	queryBase:" + queryBase);
		System.out.println("	queryFrom:" + queryFrom);
		System.out.println("	queryWhere:" + String.valueOf(queryWhere));

		if ((queryFrom == null) || (queryFrom.length() <= 0)) {
			throw new RuntimeException("parseQuery(String) looks like the from expression didn't survive parsing");
		}

		ret = "//" + queryFrom;

		if ((queryBase == null) || (queryBase.length() <= 0)) {
			throw new RuntimeException("parseQuery(String) looks like the query base didn't survive parsing");
		}

		if (! queryBase.equals("*")) {
			ret = ret + "/" + queryBase;
		}

		if ((queryWhere != null) && (queryWhere.length() > 0)) {
			/*Realistically there is a bit of fun to be had here. I think what I would do is
			tokenize the query into terms based on equality and then order those terms into a
			tree based on operators. The tree would then be "collapsed" upward until the top level
			node contains the completed xpath.*/
			//EQUALITIES(IS, NULL, ETC)
			//PARENS
			//NOT
			//AND
			//OR

			//EXAMPLES:
			//	A && B
			//	//plugin/config[module='XMLDataStorePlugin'][key='active']
			//
			//	(B || C)
			//	//plugin/config[key='active']|//plugin/config[key="storage"]
			//
			//	A && (B || C)
			//	//plugin/config[module='XMLDataStorePlugin'][key='active']|//plugin/config[module='XMLDataStorePlugin'][key="storage"]
			//
			//	A || (B && C)
			//	//plugin/config[sequence='0']|//plugin/config[key='storage'][value='true']

			throw new RuntimeException("parseQuery(String) unfortunately this method cannot yet handle the level of sophistication embodied in the query");
		}

		System.out.println("	ret: " + ret);
		return ret;
	}

	public Collection query (String strQuery) throws IllegalArgumentException {
		System.out.println("XMLDataStorePlugin.query(String)");
		strQuery = strQuery.trim().toLowerCase();
		Vector results = new Vector();

		for (String resource: resources) {
			results.addAll(query(resource, parseQuery(strQuery)));
		}

		return results;
	}

	/*When I started writing this I had intended to use XQuery, but for some
	reason I got well into parseQuery before I realized I was using XPath.
	Massive blunder aside, this is really all prototype and I expect this
	plugin to be completely re-written at some point so, I guess I'll just
	finish it as it is.*/
	private Collection query (String resource, String strQuery) {
		System.out.println("XMLDataStorePlugin.query(String, String)");
		//System.out.println("	resource: " + resource);
		Vector results = new Vector();
		File resourceFile = new File(resource);

		try {
			if (resourceFile.isFile()) {
				//System.out.println("		resource is a file...");
				String resourceExtension = resourceFile.getCanonicalPath();

				if (resourceExtension.contains(".")) {
					resourceExtension = resourceExtension.substring(resourceExtension.lastIndexOf(".") + 1).trim().toLowerCase();
				}

				//System.out.println("		resourceExtension: " + resourceExtension);

				if (resourceExtension.equals("xml")) {
					System.out.println("		resource is an xml file..." + resource);
					//DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
					DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
					Document doc = builder.parse(resourceFile);
					//XPathFactory xpFactory = XPathFactory.newInstance();
					XPath xpath = XPathFactory.newInstance().newXPath();
					XPathExpression expression = xpath.compile(strQuery);
					doc.getDocumentElement().normalize();

					try {
						NodeList nl = (NodeList)expression.evaluate(doc, XPathConstants.NODESET);

						for (int i = 0; i < nl.getLength(); i++) {
							Node n = nl.item(i);
							results.add(objectFromNode(n));
						}
					}
					catch (Exception exc) {
						exc.printStackTrace();
					}
				}
			}
			else {
				for (String child : resourceFile.list()) {
					results.addAll(query((resourceFile.getCanonicalPath() + File.separator + child), strQuery));
				}
			}
		}
		catch (Exception exc) {
			exc.printStackTrace();
		}

		return results;
	}

	public void persist (Object in) throws IllegalArgumentException {
		//
	}

	/*Turn a NodeList into an object*/
	private Object objectFromNode (Node n) {
		if (n == null) {
			throw new RuntimeException ("objectFromNodeList(NodeList) NodeList is null");
		}

		if (n.getChildNodes().getLength() <= 0) {
			throw new RuntimeException ("objectFromNodeList(NodeList) NodeList is empty");
		}

		Object ret = null;
		String type = n.getNodeName().trim();
		type = type.substring(0,1).toUpperCase() + type.substring(1);

		//child.getNodeName() should denote the object, so cast to an object of this type
		//
		//options
		//	1.) use AC to lookup appropriate methods
		//		the ac doesn't have core data types in at this point, unless
		//		I go back and add them, which would be quite a bit of stuff.
		//	2.) use reflection to map elements to properties
		//	3.) use JAXB

		if (type == null) {
			throw new RuntimeException("objectFromNodeList(NodeList) unable to determine object type");
		}

		try {
			Class klass = Class.forName("net.scientifichooliganism.xmlplugin.bindings.XML" + type);
			JAXBContext context = JAXBContext.newInstance(klass);
			Unmarshaller outlaw = context.createUnmarshaller();
			Object obj = klass.cast(outlaw.unmarshal(n));
			//set the label on the object.
			String label = n.getBaseURI();

			if (label == null) {
				throw new RuntimeException("objectFromNodeList(NodeList) label is null");
			}

			if (label.length() <= 0) {
				throw new RuntimeException("objectFromNodeList(NodeList) label is empty");
			}

			label = label.substring(label.indexOf("/") + 1).trim();

			if (label.length() <= 0) {
				throw new RuntimeException("objectFromNodeList(NodeList) label is empty after doing things to it");
			}

			((ValueObject)obj).setLabel(label);
			//System.out.println(obj);
			ret = obj;
		}
		catch (Exception exc) {
			exc.printStackTrace();
		}

		return ret;
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

		System.out.println("XMLDataStorePlugin.addResource(String)");
		//System.out.println("	adding resource: " + resource);
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