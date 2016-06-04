package net.scientifichooliganism.xmlplugin.bindings;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import net.scientifichooliganism.javaplug.vo.Configuration;

@XmlRootElement(name="config")
public class XMLConfig extends Configuration {
	public XMLConfig () {
		super();
	}

	@Override
	@XmlElement(name="id")
	public void setID(int in) {
		super.setID(in);
	}
}