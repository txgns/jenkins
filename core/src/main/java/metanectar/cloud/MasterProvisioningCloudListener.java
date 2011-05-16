package metanectar.cloud;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.slaves.Cloud;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A listener for master provisioning cloud related events.
 *
 * @author Paul Sandoz
 */
public abstract class MasterProvisioningCloudListener implements ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(MasterProvisioningCloudListener.class.getName());

    /**
     * Called when a node is to be provisioned from a cloud.
     *
     * @param c the cloud.
     */
    public void onProvisioning(Cloud c) {}

    /**
     * Called when a node provisioning error occurs.
     *
     * @param c the cloud.
     * @param t the error.
     */
    public void onProvisioningError(Cloud c, Throwable t) {}

    /**
     * Called when a node is provisioned from a cloud.
     *
     * @param c the cloud.
     * @param n the node created from the cloud.
     */
    public void onProvisioned(Cloud c, Node n) {}

    /**
     * Called when a node terminating error occurs.
     *
     * @param n the node.
     * @param t the error.
     */
    public void onTerminatingError(Node n, Throwable t) {}

    /**
     * Called when a node is terminated.
     *
     * @param n the node.
     */
    public void onTerminated(Node n) {}

    // Event firing

    public static final void fireOnProvisioning(final Cloud c) {
        fire (new FireLambda() {
            public void f(MasterProvisioningCloudListener mpcl) {
                mpcl.onProvisioning(c);
            }
        });
    }

    public static final void fireOnProvisioningError(final Cloud c, final Throwable t) {
        fire (new FireLambda() {
            public void f(MasterProvisioningCloudListener mpcl) {
                mpcl.onProvisioningError(c, t);
            }
        });
    }

    public static final void fireOnProvisioned(final Cloud c, final Node n) {
        fire (new FireLambda() {
            public void f(MasterProvisioningCloudListener mpcl) {
                mpcl.onProvisioned(c, n);
            }
        });
    }

    public static final void fireOnTerminatingError(final Node n, final Throwable t) {
        fire (new FireLambda() {
            public void f(MasterProvisioningCloudListener mpcl) {
                mpcl.onTerminatingError(n, t);
            }
        });
    }

    public static final void fireOnTerminated(final Node n) {
        fire (new FireLambda() {
            public void f(MasterProvisioningCloudListener mpcl) {
                mpcl.onTerminated(n);
            }
        });
    }

    private interface FireLambda {
        void f(MasterProvisioningCloudListener m);
    }

    private static void fire(FireLambda l) {
        for (MasterProvisioningCloudListener mpcl : MasterProvisioningCloudListener.all()) {
            try {
                l.f(mpcl);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception when firing event", e);
            }
        }
    }

    public static ExtensionList<MasterProvisioningCloudListener> all() {
        return Hudson.getInstance().getExtensionList(MasterProvisioningCloudListener.class);
    }
}
