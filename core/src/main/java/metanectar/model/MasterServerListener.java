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

    /**
     * Called when the state of the master changes.
     *
     * @param ms the master.
     */
    public abstract void onStateChange(MasterServer ms);

    public static ExtensionList<MasterServerListener> all() {
        return Hudson.getInstance().getExtensionList(MasterServerListener.class);
    }

    /* package */ final static void fireOnStateChange(final MasterServer ms) {
        fire (new FireLambda() {
            public void f(MasterServerListener msl) {
                msl.onStateChange(ms);
            }
        });
    }

    private interface FireLambda {
        void f(MasterServerListener msl);
    }

    private static void fire(FireLambda l) {
        for (MasterServerListener msl : MasterServerListener.all()) {
            try {
                l.f(msl);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception when firing event", e);
            }
        }
    }

}
