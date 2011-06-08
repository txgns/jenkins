package metanectar.proxy;

import metanectar.model.MasterServer;
import metanectar.test.MetaNectarTestCase;

/**
 *
 * @author Paul Sandoz
 */
public class NoGlobalEndpointTest extends MetaNectarTestCase {

    public void testGlobalEndpoint() throws Exception {
        MasterServer ms = metaNectar.createManagedMaster("foo");

        assertNull(ms.getEndpoint());
        assertNull(ms.getGlobalEndpoint());
    }
}
