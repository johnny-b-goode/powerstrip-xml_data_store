package net.scientifichooliganism.xmlplugin.bindings;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import net.scientifichooliganism.javaplug.vo.Action;

@XmlRootElement(name="action")
public class XMLAction extends Action {

	public XMLAction () {
		super();
	}

	@Override
	@XmlElement(name="id")
	public void setID(int in) {
		super.setID(in);
	}

	@Override
	@XmlElement(name="class")
	public void setKlass (String in) throws IllegalArgumentException {
		super.setKlass(in);
	}
}