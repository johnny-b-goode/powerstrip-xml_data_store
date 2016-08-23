package net.scientifichooliganism.xmldatastore;

import net.scientifichooliganism.javaplug.interfaces.Environment;
import net.scientifichooliganism.javaplug.interfaces.MetaData;
import net.scientifichooliganism.javaplug.query.Query;
import net.scientifichooliganism.javaplug.vo.BaseEnvironment;
import net.scientifichooliganism.javaplug.vo.BaseMetaData;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

public class XMLDataStorePluginTest {
    private XMLDataStorePlugin plugin;

    public XMLDataStorePluginTest () {
        plugin = null;
    }

    @Before
    public void init () {
        plugin = XMLDataStorePlugin.getInstance();
    }

    @Test
    public void test01 () {
        plugin.addResource("C:\\Users\\tyler.hartwig\\Code\\SVN Repos\\JavaPlug-XMLDataStore\\trunk\\rsrc\\XMLDataStorePlugin.xml");

        Query query = new Query("Configuration WHERE Configuration.Key == \"provides\"");
        plugin.query(query);

        MetaData data = new BaseMetaData();
        data.setKey("key");
        data.setValue("value");
        data.setSequence(1);
        Environment vo = new BaseEnvironment();
        vo.setID("1");
        vo.addMetaData(data);

        plugin.persist(vo);
        Collection environments = plugin.query(new Query("Environment"));
        Environment remove = (Environment)environments.iterator().next();
        plugin.remove(remove);
    }
}
