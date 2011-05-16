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
    /**
     * Called when the state of the master changes.
     *
     * @param ms the master.
     */
    public abstract void onStateChange(MasterServer ms);

    /**
     * Called when the master is connected.
     *
     * @param ms the master.
     */
    public abstract void onConnected(MasterServer ms);

    /**
     * Called when the master is disconnected.
     *
     * @param ms the master.
     */
    public abstract void onDisconnected(MasterServer ms);

    public static ExtensionList<MasterServerListener> all() {
        return Hudson.getInstance().getExtensionList(MasterServerListener.class);
    }
}
