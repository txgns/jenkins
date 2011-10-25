package metanectar.model;

import com.cloudbees.commons.metanectar.provisioning.ComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.FutureComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.LeaseId;
import com.cloudbees.commons.metanectar.provisioning.ProvisioningException;
import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import hudson.model.TaskListener;

import java.util.Collection;

/**
 * An owner of slaves who is willing to lend them out to tenants.
 */
public interface SlaveTrader {
    /**
     * If the slave manager is capable of provisioning a slave that matches the given label expression,
     * return true.
     * @param labelExpression the label expression.
     * @return {@code false} if there is no way the label expression can be provisioned.
     */
    boolean canProvision(String labelExpression);

    /**
     * List all the label atoms that this manager understands.
     * <p/>
     * This is informational and used for auto-completion etc.
     * @return The collection of labels that this manager understands.
     */
    Collection<String> getLabels();

    /**
     * Allocate one slave.
     * <p/>
     * Since this method only allocates one slave, it may have less executors than the caller wants.
     * It is the caller's responsibility to check that and make additional
     * {@link #provision(String, String, hudson.model.TaskListener,int)} calls.
     * This design allows the caller to pass in one {@link hudson.model.TaskListener} per slave, thereby making it
     * easier to distinguish multiple provisioning activities in progress.
     *
     * @param labelExpression Specifies the slave that needs to be provisioned.
     * @param listener        Receives the progress/error report on the provisioning activity.
     *                        This object will keep getting updates asynchronously while the provisioning is in
     *                        progress.
     * @param numOfExecutors  Number of executors in demand.
     * @return Provisioning activity that's in progress. Never null.
     * @throws com.cloudbees.commons.metanectar.provisioning.ProvisioningException if something went wrong with the provisioning.
     */
    FutureComputerLauncherFactory provision(String tenant, String labelExpression, TaskListener listener, int numOfExecutors)
            throws ProvisioningException;

    /**
     * Inform the {@link SlaveManager} that the allocated slave is no longer required. Customers of the
     * {@link SlaveManager} are not required to ever call this method, but it is considered best practice to call it
     * when you know that you do not need the allocated slave. Implementers of {@link SlaveManager} should have
     * their own mechanism for tracking whether a slave lease is still in use, but can use this method to eagerly
     * update their tracking system.
     *
     * @param allocatedSlave the allocated slave.
     */
    void release(ComputerLauncherFactory allocatedSlave);

    /**
     * Returns {@code true} if the allocated slave is still considered as allocated. In the event of a loss of
     * communication between a remote {@link SlaveManager} and the customer of the {@link SlaveManager}, this
     * method can be use to confirm the validity of the lease. <strong>NOTE:</strong> it is not intended that this
     * method be polled continuously, this method is only to be used in two cases:
     * <ul>
     * <li>The customer of the {@link SlaveManager} persisted the lease prior to restarting its JVM and is
     * now needing to re-verify the lease validity
     * </li>
     * <li>The customer of the {@link SlaveManager} lost communication with the remote process hosting the
     * {@link SlaveManager} and has now re-established that communication
     * </li>
     * </ul>
     *
     * @param id the allocated slave.
     * @return {@code true} if the lease is still valid.
     */
    boolean isProvisioned(LeaseId id);

    String getUid();
}
