package metanectar.proxy;

import metanectar.Config;
import metanectar.model.MasterServer;
import metanectar.test.MetaNectarTestCase;

import java.io.File;
import java.util.Properties;

/**
 *
 * @author Paul Sandoz
 */
public class GlobalEndpointTest extends MetaNectarTestCase {

    private String proxyUrl = "http://remote:80/base";

    @Override
    protected void setUp() throws Exception {
        Properties p = new Properties();
        p.setProperty("metaNectar.proxy.url", proxyUrl);
        setConfig(new Config(p));
        super.setUp();
    }

    public void testGlobalEndpoint() throws Exception {
        MasterServer ms = metaNectar.createMasterServer("foo");

        String expected = "http://remote:80/base" + "/" + ms.getIdName();

        assertEquals(expected, ms.getEndpoint().toExternalForm());
        assertEquals(expected, ms.getGlobalEndpoint().toExternalForm());
    }
}
