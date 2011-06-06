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

    public static ExtensionList<ConnectedMasterListener> all() {
        return Hudson.getInstance().getExtensionList(ConnectedMasterListener.class);
    }

    /* package */ final static void fireOnConnected(final ConnectedMaster cm) {
        fire (new FireLambda() {
            public void f(ConnectedMasterListener cml) {
                cml.onConnected(cm);
            }
        });
    }

    /* package */ final static void fireOnDisconnected(final ConnectedMaster cm) {
        fire (new FireLambda() {
            public void f(ConnectedMasterListener cml) {
                cml.onDisconnected(cm);
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
