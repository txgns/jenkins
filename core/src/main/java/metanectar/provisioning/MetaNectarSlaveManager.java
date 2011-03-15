package metanectar.provisioning;

import hudson.model.Label;
import hudson.model.TaskListener;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public interface MetaNectarSlaveManager {
    /**
     * If the slave manager is capable of provisioning a slave that matches the given label expression,
     * return true.
     */
    boolean canProviosion(Label label) throws IOException, InterruptedException;

    /**
     * Allocate one slave.
     *
     * <p>
     * Since this method only allocates one slave, it may have less executors than the caller wants.
     * It is the caller's responsibility to check that and make additional {@link #provision(Label, TaskListener, int)} calls.
     * This design allows the caller to pass in one {@link TaskListener} per slave, thereby making it
     * easier to distinguish multiple provisioning activities in progress.
     *
     * @param label
     *      Specifies the slave that needs to be provisioned.
     * @param listener
     *      Receives the progress/error report on the provisioning activity.
     *      This object will keep getting updates asynchronously while the provisioning is in progress.
     * @param numOfExecutors
     *      Number of executors in demand.
     * @return
     *      Provisioning activity that's in progress. Never null.
     */
    ProvisioningActivity provision(Label label, TaskListener listener, int numOfExecutors) throws IOException, InterruptedException;
}
