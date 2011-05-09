package metanectar.proxy;

import metanectar.model.AbstractMasterServerListener;
import metanectar.model.MasterServer;

/**
 * A listener that prods the reserve proxy to reload it's routes when a master is
 * provisioned or terminated.
 *
 * @author Paul Sandoz
 */
public class ReverseProxyProdder extends AbstractMasterServerListener {

    public void onProvisioned(MasterServer ms) {
        prod();
    }

    public void onTerminated(MasterServer ms) {
        prod();
    }

    private void prod() {
        // TODO
        // prod the reverse proxy asynchronously
    }

}
