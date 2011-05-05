package metanectar.provisioning;

import hudson.DescriptorExtensionList;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.ExtensionPoint;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * The master provision service capable of provisioning, starting, stopping and terminating masters.
 *
 * @author Paul Sandoz
 */
public abstract class MasterProvisioningService extends AbstractDescribableImpl<MasterProvisioningService> implements ExtensionPoint {

    public static final String PROPERTY_PROVISION_GRANT_ID = "grant";

    /**
     * Provision a master.
     *
     * @param channel the channel on which remote execution can be performed to provision a new master.
     * @param listener the task listener to log information
     * @param id a unique number assigned to the master that is always less than or equal to the number of masters
     *        provisioned or being provisioned that are associated with this service.
     *        This may be used to assign a unique HTTP port to a master for when the master is started.
     * @param name the unique name of the master.
     * @param metaNectarEndpoint the MetaNectar URL, so that masters can make contact.
     * @param properties a map of properties for provisioning.
     * @return a future, when done provisioning is complete or there an error.
     * @throws Exception
     */
    public abstract Future<Master> provision(VirtualChannel channel, TaskListener listener,
                                             int id, String name, URL metaNectarEndpoint,
                                             Map<String, Object> properties) throws Exception;

    /**
     * Start a provisioned master.
     *
     * @param channel
     * @param listener the task listener to log information
     * @param name the name of the master.
     * @return a future, when done starting is complete or there is an error.
     * @throws Exception
     */
    public abstract Future<?> start(VirtualChannel channel, TaskListener listener,
                                    String name) throws Exception;

    /**
     * Stop a started master.
     *
     * @param channel
     * @param listener the task listener to log information
     * @param name the name of the master.
     * @return a future, when done stopping is complete or there is an error.
     * @throws Exception
     */
    public abstract Future<?> stop(VirtualChannel channel, TaskListener listener,
                                    String name) throws Exception;

    /**
     * Terminate a provisioned master.
     *
     * @param channel the channel on which remote execution can be performed to terminate a provisioned masters.
     * @param listener the task listener to log information
     * @param name the name of the master.
     * @param clean if true then any local resources, such as home and workspace directories, will be cleaned up.
     * @return a future, when done termination is complete or there is an error.
     * @throws Exception
     */
    public abstract Future<?> terminate(VirtualChannel channel, TaskListener listener,
                                        String name, boolean clean) throws Exception;

    /**
     * Returns all the registered {@link MasterProvisioningService} descriptors.
     */
    public static DescriptorExtensionList<MasterProvisioningService, Descriptor<MasterProvisioningService>> all() {
        return Hudson.getInstance().<MasterProvisioningService,Descriptor<MasterProvisioningService>>getDescriptorList(MasterProvisioningService.class);
    }
}