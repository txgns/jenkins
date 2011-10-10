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
     * Called when the master's configuration is saved.
     *
     * @param cm the master.
     */
    public void onSaved(ConnectedMaster cm) {}

    /**
     * Called when the master's configuration is loaded.
     *
     * @param cm the master.
     * @see #onCreated(ConnectedMaster)
     */
    public void onLoaded(ConnectedMaster cm) {}

    /**
     * Called when the master is created from scratch.
     *
     * @param cm the master.
     * @see #onLoaded(ConnectedMaster)
     */
    public void onCreated(ConnectedMaster cm) {}

    public static ExtensionList<ConnectedMasterListener> all() {
        return Hudson.getInstance().getExtensionList(ConnectedMasterListener.class);
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

    /* package */ static void fireOnSaved(final ConnectedMaster cm) {
        fire (new FireLambda() {
            public void f(ConnectedMasterListener cml) {
                cml.onSaved(cm);
            }
        });
    }

    /* package */ static void fireOnCreated(final ConnectedMaster cm) {
        fire (new FireLambda() {
            public void f(ConnectedMasterListener cml) {
                cml.onCreated(cm);
            }
        });
    }

    /* package */ static void fireOnLoaded(final ConnectedMaster cm) {
        fire (new FireLambda() {
            public void f(ConnectedMasterListener cml) {
                cml.onLoaded(cm);
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
