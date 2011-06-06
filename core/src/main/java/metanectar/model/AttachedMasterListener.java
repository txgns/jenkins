package metanectar.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A listener for attached master related events.
 *
 * @author Paul Sandoz
 */
public abstract class AttachedMasterListener implements ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(AttachedMasterListener.class.getName());

    /**
     * Called when the state of the master changes.
     *
     * @param am the master.
     */
    public abstract void onStateChange(AttachedMaster am);

    public static ExtensionList<AttachedMasterListener> all() {
        return Hudson.getInstance().getExtensionList(AttachedMasterListener.class);
    }

    /* package */ final static void fireOnStateChange(final AttachedMaster am) {
        fire (new FireLambda() {
            public void f(AttachedMasterListener aml) {
                aml.onStateChange(am);
            }
        });
    }

    private interface FireLambda {
        void f(AttachedMasterListener aml);
    }

    private static void fire(FireLambda l) {
        for (AttachedMasterListener aml : AttachedMasterListener.all()) {
            try {
                l.f(aml);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception when firing event", e);
            }
        }
    }

}
