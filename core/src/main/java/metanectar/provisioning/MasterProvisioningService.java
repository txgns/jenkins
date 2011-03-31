package metanectar.provisioning;

import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * The master provision service capable of provisioning, deleting and obtaining provisioned masters.
 * <p>
 * TODO listener or task listener
 *
 * @author Paul Sandoz
 */
public interface MasterProvisioningService {
    /**
     * Provision a new master.
     *
     * @param channel the channel on which remote execution can be performed to provision a new master.
     * @param organization the organization to be associated with the master.
     * @param metaNectarEndpoint the MetaNectar URL, so that masters can make contact.
     * @param properties a map of properties for provisioning.
     * @return a future of the master, when the future is done the master is considered provisioned.
     * @throws IOException
     * @throws InterruptedException
     */
    Future<Master> provision(VirtualChannel channel, String organization, URL metaNectarEndpoint,
                                               Map<String, String> properties) throws IOException, InterruptedException;

    /**
     * Delete a provisioned master.
     *
     * @param channel the channel on which remote execution can be performed to delete a provisioned masters.
     * @param organization the organization associated with the master.
     * @param clean if true then any local resources, such as home and workspace directories, will be cleaned up.
     * @return a future, when done the provisioned master is considered deleted.
     * @throws IOException
     * @throws InterruptedException
     */
    Future<?> delete(VirtualChannel channel, String organization, boolean clean) throws IOException, InterruptedException;

    /**
     * Get the provisioned masters.
     *
     * @param channel the channle on which remote execution can be performed to get the provisioned masters.
     * @return a map of organization to master.
     */
    Map<String, Master> getProvisioned(VirtualChannel channel);
}