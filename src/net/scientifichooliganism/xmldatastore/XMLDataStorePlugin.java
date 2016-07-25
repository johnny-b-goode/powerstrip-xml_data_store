package net.scientifichooliganism.xmldatastore;

import net.scientifichooliganism.javaplug.ActionCatalog;
import net.scientifichooliganism.javaplug.DataLayer;
import net.scientifichooliganism.javaplug.interfaces.*;
import net.scientifichooliganism.javaplug.query.Query;
import net.scientifichooliganism.javaplug.query.QueryNode;
import net.scientifichooliganism.javaplug.query.QueryOperator;
import net.scientifichooliganism.javaplug.query.QueryResolver;
import net.scientifichooliganism.javaplug.vo.BaseAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

/**
* This class does stuff, maybe.
*/
public class XMLDataStorePlugin implements Plugin, Store {
	private static XMLDataStorePlugin instance;
	private Vector<String> resources;
	private Vector<String> supportedTypes;
    private ActionCatalog ac;
	private DataLayer dl;

    private static final String XML_PLUGIN = "XMLPlugin";
    private static final String XML_PLUGIN_PATH = "net.scientifichooliganism.xmlplugin." + XML_PLUGIN;

    private String defaultFile;
	private boolean configured = false;
	private static boolean configuring;

	private XMLDataStorePlugin() {
		resources = new Vector<String>();
		supportedTypes = new Vector<>();
        ac = ActionCatalog.getInstance();
		dl = null;
	}

	public static XMLDataStorePlugin getInstance() {
		if (instance == null) {
			instance = new XMLDataStorePlugin();
		}

		if(!instance.configured){
			if(!configuring) {
				configuring = true;
				instance.tryConfigure();
				configuring = false;
			}
		}

		return instance;
	}

	// TODO: add method to store interface for configuring
	public void tryConfigure(){
		dl = DataLayer.getInstance();
		// Temporary try
		try {
			Vector<Configuration> configs = (Vector<Configuration>) dl.query("Configuration FROM XMLDataStorePlugin");
			for (Configuration config : configs) {
				if (config.getKey().equals("default_file")) {
					defaultFile = config.getValue();
					addResource(defaultFile);
					configured = true;
				} else if(config.getKey().equals("provides") &&
						config.getModule().equals("XMLDataStorePlugin")){
					supportedTypes.add(config.getValue());
				}
			}
		} catch (Exception exc){

		}
	}

	public void validateQuery (String query) throws IllegalArgumentException {
//		System.out.println("XMLDataStorePlugin.validateQuery(String)");
//        System.out.println("    Validating query: " + query);
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

//		System.out.println("	query successfully validated: " + query);
	}

	private String getSimpleClassName(String fullyQualifiedName){
		boolean found = false;
		String simpleClassName = null;
		while(!found){
			try {
				Class klass = Class.forName(fullyQualifiedName);
                found = true;
				simpleClassName = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf(".") + 1);
			} catch(ClassNotFoundException exc){
				// This is not yet a valid class name, it must have
				// properties, remove a property so we can try again.
                if(fullyQualifiedName.contains(".")) {
					fullyQualifiedName = fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf("."));
				} else {
					// Assume we found simple class name since no more . exist
					simpleClassName = fullyQualifiedName;
					found = true;
				}
			}
		}

		return simpleClassName;
	}

	private String getPredicateFromTree(QueryNode node){
	    if(node.isOperator()){
			QueryOperator operator = node.getOperator();
			QueryNode leftChild = null, rightChild = null;
            String operation = null;

            if(node.getLeftChild() != null && node.getRightChild() != null){
            	leftChild = node.getLeftChild();
				rightChild = node.getRightChild();
			} else {
				throw new RuntimeException("getPredicateFromTree(QueryNode) bad tree");
			}

			switch(operator){
				case AND:
					operation = " and ";
					break;
				case OR:
				    operation = " or ";
					break;
				case EQUAL:
					operation = "=";
                    break;
				case GREATER_THAN:
					operation = ">";
					break;
				case GREATER_EQUAL:
				    operation = ">=";
					break;
				case LESS_THAN:
				    operation = "<";
					break;
				case LESS_EQUAL:
					operation = "<=";
					break;
				default:
				    throw new RuntimeException("getPredicateFromTree(QueryNode) - bad operator " + operator.toString());
			}

			String leftSide = getPredicateFromTree(leftChild);
			String rightSide = getPredicateFromTree(rightChild);
			if(leftSide.split("\\[").length > leftSide.split("]").length) {
				rightSide += "]";
			}

			return leftSide + operation + rightSide;
		} else if(node.isProperty()){
			String fullName = node.getValue();
			String className = getSimpleClassName(fullName);
			String property = fullName.substring(fullName.lastIndexOf(className) + className.length() + 1);

			// check that only one . exists in the string
			if(property.split("\\.").length - 1 == 1){
				property = property.replace(".", "[");
			} else {
				throw new RuntimeException("getPredicateFromTree(QueryNode) - Bad Property Expression " + fullName);
			}

			return property;
		} else if(node.isLiteral()){
			return node.getValue();
		}
		return null;
	}

	private Set<String> classesInQuery(QueryNode node, Set<String> classNames){
		if(node.isProperty()){
			String name = getSimpleClassName(node.getValue());
            classNames.add(name);
		}
		if(node.getLeftChild() != null){
			classesInQuery(node.getLeftChild(), classNames);
		}
		if(node.getRightChild() != null){
			classesInQuery(node.getRightChild(), classNames);
		}

		return classNames;
	}

	private String parseQuery (Query query) throws IllegalArgumentException, RuntimeException {
		System.out.println("XMLDataStorePlugin.parseQuery(String)");
        QueryNode tree = query.buildTree();
		Set<String> classes = new TreeSet<>();
		if(tree != null) {
			tree.removeNots();
			classes = classesInQuery(tree, new TreeSet<>());
		}
		String ret = "";
        for(String klass : query.getSelectValues()){
        	klass = getSimpleClassName(klass);
        	classes.add(klass);
		}

		if(classes.size() > 1){
		    for(String klass : classes){
		    	ret += "//" + klass + " | ";
			}
			ret = ret.substring(0, ret.lastIndexOf(" | "));
		} else if (classes.size() == 1){
		    String predicate = null;
            if(tree != null) {
				predicate = getPredicateFromTree(tree);
			}
            String klass = (String)classes.toArray()[0];
			klass = getSimpleClassName(klass);
			ret += "//" + klass;
            if(predicate != null) {
				ret += "[" + predicate + "]";
			}
		}

		return ret.toLowerCase();



























////		validateQuery(strQuery);
//		String queryBase = null;
//		if(query.getSelectValues().length > 0){
//			queryBase = query.getSelectValues()[0];
//		}
//
//		String queryWhere = null;
//
////		if (queryFrom.contains("where")) {
////			queryWhere = queryFrom.substring(queryFrom.indexOf("where")).trim();
////			queryWhere = queryWhere.substring(5).trim();
////			queryFrom = queryFrom.substring(0, queryFrom.indexOf("where")).trim();
////		}
//
//		System.out.println("	queryBase:" + queryBase);
//		System.out.println("	queryWhere:" + String.valueOf(queryWhere));
//
//
//
//
//		if ((queryBase == null) || (queryBase.length() <= 0)) {
//			throw new RuntimeException("parseQuery(String) looks like the query base didn't survive parsing");
//		}
//
//		if (! queryBase.equals("*")) {
//			ret = "//" + queryBase;
//		}
//
//		if ((queryWhere != null) && (queryWhere.length() > 0)) {
//			/*Realistically there is a bit of fun to be had here. I think what I would do is
//			tokenize the query into terms based on equality and then order those terms into a
//			tree based on operators. The tree would then be "collapsed" upward until the top level
//			node contains the completed xpath.*/
//			//EQUALITIES(IS, NULL, ETC)
//			//PARENS
//			//NOT
//			//AND
//			//OR
//
//			//EXAMPLES:
//			//	A && B
//			//	//plugin/config[module='XMLDataStorePlugin'][key='active']
//			//
//			//	(B || C)
//			//	//plugin/config[key='active']|//plugin/config[key="storage"]
//			//
//			//	A && (B || C)
//			//	//plugin/config[module='XMLDataStorePlugin'][key='active']|//plugin/config[module='XMLDataStorePlugin'][key="storage"]
//			//
//			//	A || (B && C)
//			//	//plugin/config[sequence='0']|//plugin/config[key='storage'][value='true']
//
//			throw new RuntimeException("parseQuery(String) unfortunately this method cannot yet handle the level of sophistication embodied in the query");
//		}
//
////		System.out.println("	ret: " + ret);
//		return ret;
	}

	public Collection query (Query query) throws IllegalArgumentException {
		System.out.println("XMLDataStorePlugin.query(String)");
		Vector results = new Vector();

        String parsedQuery = parseQuery(query);
		System.out.println("    " + resources.size() + " to query!");
		for (String resource: resources) {
			System.out.println("    Using resource: " + resource);
			results.addAll(query(resource, parsedQuery));
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
		System.out.println("	resource: " + resource);
		Vector results = new Vector();
		File resourceFile = new File(resource);

		try {
            if(resourceFile.exists()) {
                if (resourceFile.isFile()) {
				System.out.println("		resource is a file...");
                    String resourceExtension = resourceFile.getCanonicalPath();

                    if (resourceExtension.contains(".")) {
                        resourceExtension = resourceExtension.substring(resourceExtension.lastIndexOf(".") + 1).trim().toLowerCase();
                    }

//				System.out.println("		resourceExtension: " + resourceExtension);

                    if (resourceExtension.equals("xml")) {
//					System.out.println("		resource is an xml file..." + resource);
                        //DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                        Document doc = builder.parse(resourceFile);
                        //XPathFactory xpFactory = XPathFactory.newInstance();
                        XPath xpath = XPathFactory.newInstance().newXPath();
                        XPathExpression expression = xpath.compile(strQuery);
                        doc.getDocumentElement().normalize();

                        try {
                            NodeList nl = (NodeList) expression.evaluate(doc, XPathConstants.NODESET);

                            for (int i = 0; i < nl.getLength(); i++) {
                                Node n = nl.item(i);

                                ValueObject result = (ValueObject) ac.performAction(XML_PLUGIN, XML_PLUGIN_PATH, "objectFromNode", new Object[]{n});
//								ValueObject result = (ValueObject) XMLPlugin.getInstance().objectFromNode(n);
                                result.setLabel(result.getLabel() + "|" + strQuery);

								results.add(result);
                            }
                        } catch (Exception exc) {
                            exc.printStackTrace();
                        }
                    }
                } else {
                    for (String child : resourceFile.list()) {
                        results.addAll(query((resourceFile.getCanonicalPath() + File.separator + child), strQuery));
                    }
                }
            }
		}
		catch (Exception exc) {
			exc.printStackTrace();
		}

		return results;
	}

	public void persist (Object in) throws IllegalArgumentException {
        ValueObject vo = (ValueObject)in;
        String fileName;
        // ValueObject's should have their file name at the front of their label at this point
        if(vo.getLabel() != null) {
            fileName = vo.getLabel().split("\\|")[0];
            vo.setLabel(vo.getLabel().replaceAll(fileName + "\\|", ""));
        } else {
            fileName = defaultFile;
			if(fileName == null){
				throw new RuntimeException("persist(Object) default file not configured in XMLDataStorePlugin");
			}
        }

        persist(fileName, in);
	}

    private void persist(String resource, Object in){
		ValueObject vo = (ValueObject)in;
        try {
            // resource should be a string to the exact file location
            File file = new File(resource);
            Document document;

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            if(file.exists()) {
                document = builder.parse(file);
                document.getDocumentElement().normalize();
            } else {
                document = builder.newDocument();
				document.appendChild(document.createElement("data"));
                file.createNewFile();
            }

			if(vo.getID() == null){
				vo.setID(dl.getUniqueID());
			}

			Node insertNode = null;
            if(vo.getLabel() != null) {
                String queryStr = vo.getLabel();

				queryStr += "[id=" + vo.getID() + "]";

                XPath xpath = XPathFactory.newInstance().newXPath();
                XPathExpression expression = xpath.compile(queryStr);

                Node node = (Node) expression.evaluate(document, XPathConstants.NODE);
                if(node != null){
                    node.getParentNode().removeChild(node);
					insertNode = node.getParentNode();
                }
            }

            Node resultNode = (Node)ac.performAction(XML_PLUGIN, XML_PLUGIN_PATH, "nodeFromObject", new Object[]{vo});
            Element element = resultNode.getOwnerDocument().getDocumentElement();
			Node newNode = document.importNode(element, true);

			if(insertNode == null) {
				document.getDocumentElement().appendChild(newNode);
			} else {
				insertNode.appendChild(newNode);
			}

            Transformer transformer = TransformerFactory.newInstance().newTransformer();

            StreamResult result = new StreamResult(file);
            transformer.transform(new DOMSource(document), result);
        } catch (Exception exc){
            exc.printStackTrace();
        }
    }


	/**a bunch of tests, I mean, a main method*/
	public static void main (String [] args) {
	    Query query = QueryResolver.getInstance().resolve("Action WHERE Action.data.value == \"10\"");
		XMLDataStorePlugin plugin = XMLDataStorePlugin.getInstance();
		plugin.addResource("C:\\Users\\tyler.hartwig\\Code\\SVN Repos\\JavaPlug-XMLDataStore\\trunk\\rsrc\\XMLDataStorePlugin.xml");

		Collection results = plugin.query(query);

		System.out.println(results);
		try {
			//dl.query(null);
			//dl.query("");
			//dl.query(" ");

			Action action = new BaseAction();
			action.setName("My Action Name");
			action.setMethod("New method");
			action.setURL("google.com");

			XMLDataStorePlugin.getInstance().persist(action);

			Collection actions = XMLDataStorePlugin.getInstance().query(QueryResolver.getInstance().resolve("Action FROM data"));

			Action changeAction = (Action)actions.iterator().next();

			changeAction.setName("NEW NAME!");
			XMLDataStorePlugin.getInstance().persist(changeAction);

		}
		catch (Exception exc) {
			exc.printStackTrace();
		}
	}

	public String[][] getActions() {
		String actions[][] = new String [1][3];
		//actions[0] = {"XMLDataStorePlugin", "net.scientifichooliganism.xmldatastore", "printMessage"};
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
		System.out.println("	adding resource: " + resource);
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