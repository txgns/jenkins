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
public class NoGlobalEndpointTest extends MetaNectarTestCase {

    public void testGlobalEndpoint() throws Exception {
        MasterServer ms = metaNectar.createMasterServer("foo");

        assertNull(ms.getEndpoint());
        assertNull(ms.getGlobalEndpoint());
    }
}
