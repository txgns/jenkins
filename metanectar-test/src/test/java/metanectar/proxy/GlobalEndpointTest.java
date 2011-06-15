package metanectar.proxy;

import junit.framework.TestCase;
import metanectar.Config;
import metanectar.model.MasterServer;
import metanectar.test.MetaNectarTestCase;

import java.net.URL;
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

    public void testGlobalEndpointCreated() throws Exception {
        MasterServer ms = metaNectar.createManagedMaster("foo");

        assertNull(ms.getLocalEndpoint());
        assertNull(ms.getGlobalEndpoint());
        assertNull(ms.getEndpoint());
    }

    public void testGlobalEndpoint() throws Exception {
        URL local = new URL("http://local:8080/master/foo");

        MasterServer ms = metaNectar.createManagedMaster("foo");
        ms.setProvisionCompletedState("home", local);
        String expected = proxyUrl + local.getPath();

        assertEquals(expected, ms.getGlobalEndpoint().toExternalForm());
        assertEquals(expected, ms.getEndpoint().toExternalForm());
    }
}
