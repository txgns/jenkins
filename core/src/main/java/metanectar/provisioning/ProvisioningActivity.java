package metanectar.provisioning;

import hudson.model.Node;

import java.io.Serializable;
import java.util.concurrent.Future;

/**
 * Represents a provisioning in progress.
 *
 * @author Kohsuke Kawaguchi
 */
public class ProvisioningActivity implements Serializable {
    /**
     * Some human readable name that points to the slave node being allocated.
     * This need not be related to {@link Node#getNodeName()}.
     */
    public final String displayName;

    /**
     * Number of executors this node will have once it comes online.
     */
    public final int numOfExecutors;

    /**
     * {@link Future} that represents the provisioning activity.
     * When the provisioning is complete, this future will return the value.
     */
    public final Future<ProvisioningResult> future;

    public ProvisioningActivity(String displayName, int numOfExecutors, Future<ProvisioningResult> future) {
        this.displayName = displayName;
        this.numOfExecutors = numOfExecutors;
        this.future = future;
    }

    private static final long serialVersionUID = 1L;
}
