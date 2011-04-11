package metanectar.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;
import hudson.model.Node;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A listener for MasterServer related events.
 *
 * @author Paul Sandoz
 */
public abstract class MasterServerListener implements ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(MasterServerListener.class.getName());

    public void onCreated(MasterServer ms) {}

    public void onProvisioning(MasterServer ms) {}

    public void onProvisioningErrorNoResources(MasterServer ms) {}

    public void onProvisioningError(MasterServer ms, Node n) {}

    public void onProvisioned(MasterServer ms) {}

    public void onApproved(MasterServer ms) {}

    public void onConnected(MasterServer ms) {}

    public void onDisconnected(MasterServer ms) {}

    public void onTerminating(MasterServer ms) {}

    public void onTerminatingError(MasterServer ms) {}

    public void onTerminated(MasterServer ms) {}


    public static ExtensionList<MasterServerListener> all() {
        return Hudson.getInstance().getExtensionList(MasterServerListener.class);
    }
}
