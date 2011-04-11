package metanectar.provisioning;

import hudson.DescriptorExtensionList;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.ExtensionPoint;
import hudson.model.Hudson;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * The master provision service capable of provisioning, deleting and obtaining provisioned masters.
 * <p>
 * TODO listener or task listener
 * TODO require provision and terminate functions to be idempotent
 *
 * @author Paul Sandoz
 */
public abstract class MasterProvisioningService extends AbstractDescribableImpl<MasterProvisioningService> implements ExtensionPoint {

    /**
     * Provision a new master.
     *
     * @param channel the channel on which remote execution can be performed to provision a new master.
     * @param organization the organization to be associated with the master.
     * @param metaNectarEndpoint the MetaNectar URL, so that masters can make contact.
     * @param properties a map of properties for provisioning.
     * @return a future of the master, when the future is done the master is considered provisioned.
     * @throws Exception
     */
    public abstract Future<Master> provision(VirtualChannel channel, String organization, URL metaNectarEndpoint,
                                               Map<String, String> properties) throws Exception;

    /**
     * Terminate a provisioned master.
     *
     * @param channel the channel on which remote execution can be performed to terminate a provisioned masters.
     * @param organization the organization associated with the master.
     * @param clean if true then any local resources, such as home and workspace directories, will be cleaned up.
     * @return a future, when done the provisioned master is considered terminated.
     * @throws Exception
     */
    public abstract Future<?> terminate(VirtualChannel channel, String organization, boolean clean) throws Exception;

    /**
     * Get the provisioned masters.
     *
     * @param channel the channel on which remote execution can be performed to get the provisioned masters.
     * @return a map of organization to master.
     */
    public abstract Map<String, Master> getProvisioned(VirtualChannel channel);

    /**
     * Returns all the registered {@link MasterProvisioningService} descriptors.
     */
    public static DescriptorExtensionList<MasterProvisioningService,Descriptor<MasterProvisioningService>> all() {
        return (DescriptorExtensionList) Hudson.getInstance().getDescriptorList(MasterProvisioningService.class);
    }

}