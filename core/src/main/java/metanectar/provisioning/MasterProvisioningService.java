package metanectar.provisioning;

import hudson.DescriptorExtensionList;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.ExtensionPoint;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * The master provision service capable of provisioning, starting, stopping and terminating masters.
 * <p>
 * When a master is provisioned a URL to access the master will be returned, but the master will not be accessible
 * until it is started. The URL to the master will remain the same until the master is terminated.
 * <p>
 * When a master is terminated a URL to a snapshot of home directory of that master will be returned. That master
 * may be re-provisioned by passing that URL when provisioning.
 * <p>
 * When a master is deleted then the snapshot can be removed.
 *
 * @author Paul Sandoz
 */
public abstract class MasterProvisioningService extends AbstractDescribableImpl<MasterProvisioningService> implements ExtensionPoint {

    /**
     * Provisioned state of a master.
     */
    public static class Provisioned {
        private String name;

        private final URL endpoint;

        public Provisioned(String name, URL endpoint) {
            this.name = name;
            this.endpoint = endpoint;
        }

        /**
         * @return the name of the master.
         */
        public String getName() {
            return name;
        }

        /**
         * @return the URL endpoint of the provisioned master.
         */
        public URL getEndpoint() {
            return endpoint;
        }
    }

    /**
     * Terminated state of a master.
     */
    public static class Terminated {
        private String name;

        private final URL homeSnapshot;

        public Terminated(String name, URL archivedHomeDirectory) {
            this.name = name;
            this.homeSnapshot = archivedHomeDirectory;
        }

        /**
         * @return the name of the master.
         */
        public String getName() {
            return name;
        }

        /**
         * @return the snapshot URL of the home directory.
         */
        public URL getHomeSnapshot() {
            return homeSnapshot;
        }
    }

    /**
     * The provisioning grant property name.
     * <p>
     * The grant is a one off secret, a randomly generated UUID, passed from MetaNectar to the provisioned master.
     * When a master is started and connects for the first time the master must send the grant to MetaNectar.
     * MetaNectar then verifies that the grant sent from the master matches the grant it sent to the master.
     * If so the master is approved to connect to MetaNectar, otherwise the master is denied. After initial approval
     * the grant is no longer required unless a master is terminated and re-provisioned.
     */
    public static final String PROPERTY_PROVISION_GRANT_ID = "grant";

    /**
     * The provisioning home snapshot property name.
     * <p>
     * The snapshot is a URL to an archive of a master home directory.
     * When provisioning the snapshot will be used as the master home directory rather than creating a default
     * home directory as for a newly provisioned master.
     * <p>
     * A snapshot URL will be obtained when terminating a master. That same URL may be passed as a property
     * of provisioning to re-provision a master.
     */
    public static final String PROPERTY_PROVISION_HOME_SNAPSHOT = "snapshot";

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
     * @return a future that contains provisioned state.
     *         When done provisioning is complete or there an error.
     * @throws Exception
     */
    public abstract Future<Provisioned> provision(VirtualChannel channel, TaskListener listener,
                                             int id, String name, URL metaNectarEndpoint,
                                             Map<String, Object> properties) throws Exception;

    /**
     * Start a provisioned master.
     *
     * @param channel
     * @param listener the task listener to log information
     * @param name the unique name of the master.
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
     * @param name unique the name of the master.
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
     * @param name the unique name of the master.
     * @return a future that contains terminated state.
     *         When done termination is complete or there is an error.
     * @throws Exception
     */
    public abstract Future<Terminated> terminate(VirtualChannel channel, TaskListener listener,
                                        String name) throws Exception;

    /**
     * Returns all the registered {@link MasterProvisioningService} descriptors.
     */
    public static DescriptorExtensionList<MasterProvisioningService, Descriptor<MasterProvisioningService>> all() {
        return Hudson.getInstance().<MasterProvisioningService,Descriptor<MasterProvisioningService>>getDescriptorList(MasterProvisioningService.class);
    }
}