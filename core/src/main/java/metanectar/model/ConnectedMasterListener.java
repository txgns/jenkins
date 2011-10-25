package metanectar.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A listener for connected master related events.
 *
 * @author Paul Sandoz
 */
public abstract class ConnectedMasterListener implements ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(ConnectedMasterListener.class.getName());

    /**
     * Called before the master is connected.
     * <p>More specifically, called before a channel is assigned to the master.</p>
     *
     * @param cm the master.
     */
    public void onBeforeConnected(ConnectedMaster cm) {}

    /**
     * Called when the master is connected.
     *
     * @param cm the master.
     */
    public abstract void onConnected(ConnectedMaster cm);

    /**
     * Called when the master is disconnected.
     *
     * @param cm the master.
     */
    public abstract void onDisconnected(ConnectedMaster cm);

    /**
     * Called when the master's configuration is modified
     *
     * @param cm the master.
     */
    public void onModified(ConnectedMaster cm) {}

    public static ExtensionList<ConnectedMasterListener> all() {
        return Hudson.getInstance().getExtensionList(ConnectedMasterListener.class);
    }

    /* package */ static void fireOnBeforeConnected(final ConnectedMaster cm) {
        fire (new FireLambda() {
            public void f(ConnectedMasterListener cml) {
                cml.onBeforeConnected(cm);
            }
        });
    }

    /* package */ static void fireOnConnected(final ConnectedMaster cm) {
        fire (new FireLambda() {
            public void f(ConnectedMasterListener cml) {
                cml.onConnected(cm);
            }
        });
    }

    /* package */ static void fireOnDisconnected(final ConnectedMaster cm) {
        fire (new FireLambda() {
            public void f(ConnectedMasterListener cml) {
                cml.onDisconnected(cm);
            }
        });
    }

    /* package */ static void fireOnModified(final ConnectedMaster cm) {
        fire (new FireLambda() {
            public void f(ConnectedMasterListener cml) {
                cml.onModified(cm);
            }
        });
    }

    private interface FireLambda {
        void f(ConnectedMasterListener cml);
    }

    private static void fire(FireLambda l) {
        for (ConnectedMasterListener cml : ConnectedMasterListener.all()) {
            try {
                l.f(cml);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception when firing event", e);
            }
        }
    }

}
