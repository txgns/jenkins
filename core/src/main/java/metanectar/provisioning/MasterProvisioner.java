package metanectar.provisioning;

import com.google.common.collect.*;
import hudson.Extension;
import hudson.model.*;
import hudson.slaves.Cloud;
import metanectar.cloud.NodeTerminatingRetentionStrategy;
import metanectar.cloud.MasterProvisioningCloudListener;
import metanectar.model.MasterServer;
import metanectar.model.MasterServerListener;
import metanectar.model.MasterTemplate;
import metanectar.model.MetaNectar;
import metanectar.provisioning.task.*;
import metanectar.provisioning.task.TaskQueue;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.slaves.NodeProvisioner.PlannedNode;

/**
 * A master provisioner that piggy backs off slave provisioning.
 * <p>
 * Nodes and Clouds are selected that support a specific label.
 * <p>
 * The provisioning occurs in two stages. If a Node can provision a master then a request is made
 * to provision the master on that Node's channel.
 * If no Nodes can provision (because they have reached capacity) then a node with the specific label will be
 * provisioned from the Cloud.
 *
 * @author Paul Sandoz
 */
public class MasterProvisioner {

    public static final String MASTER_LABEL_ATOM_STRING = "_masters_";

    private static final Logger LOGGER = Logger.getLogger(MasterProvisioner.class.getName());

    private final MetaNectar mn;

    private Label masterLabel;

    private final ListMultimap<Node, MasterServer> nodesWithMasters = ArrayListMultimap.create();

    private class NodeUpdateListener extends MasterServerListener {
        @Override
        public void onStateChange(final MasterServer ms) {
            // The modification of nodesWithMasters is guaranteed to occur on the same thread
            // as the periodic timer, thus no synchronization is required.
            switch (ms.getState())  {
                // Assign the node for provisioning or a provisioning error
                // The latter is important for cases when the error needs to be resolved on the node
                // If the information is removed on a provisioning error the node, if provisioned, from a cloud
                // will be terminated
                case Provisioning:
                case ProvisioningError: {
                    final Node n = ms.getNode();
                    if (!nodesWithMasters.containsEntry(n, ms)) {
                        nodesWithMasters.put(n, ms);
                    }
                    break;
                }

                case Terminated:
                    nodesWithMasters.values().remove(ms);
                    break;
            }
        }
    }

    public MasterProvisioner(MetaNectar mn) {
        this.mn = mn;

        // TODO normalize states

        // Re-create node with masters map
        for (MasterServer ms : mn.getManagedMasters()) {
            if (ms.getNode() != null) {
                nodesWithMasters.put(ms.getNode(), ms);
            }
        }

        MasterServerListener.all().add(0, new NodeUpdateListener());
    }

    public ListMultimap<Node, MasterServer> getProvisionedMasters() {
        return Multimaps.unmodifiableListMultimap(nodesWithMasters);
    }

    public Label getLabel() {
        // TODO make configurable
        return MetaNectar.getInstance().getLabel(MASTER_LABEL_ATOM_STRING);
    }

    public boolean hasPendingRequests() {
        return !pendingMasterRequests.isEmpty();
    }

    public boolean cancelPendingRequest(MasterServer ms) throws IOException {
        PlannedMasterRequest pmr = getPlannedMasterRequest(ms);
        if (pmr == null)
            return false;

        return pmr.tryCancel();
    }

    private PlannedMasterRequest getPlannedMasterRequest(MasterServer ms) {
        for (PlannedMasterRequest pmr : pendingMasterRequests) {
            if (pmr.ms == ms) {
                return pmr;
            }
        }
        return null;
    }

    public void provisionAndStart(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties) throws IOException {
        ms.setPreProvisionState();
        pendingMasterRequests.add(new PlannedMasterRequest(ms, metaNectarEndpoint, properties, true));
    }

    public void stopAndTerminate(MasterServer ms) {
        masterServerTaskQueue.start(new MasterStopThenTerminateTask(ms));
    }

    public void provision(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties) throws IOException {
        ms.setPreProvisionState();
        pendingMasterRequests.add(new PlannedMasterRequest(ms, metaNectarEndpoint, properties, false));
    }

    public void start(MasterServer ms) {
        masterServerTaskQueue.start(new MasterStartTask(ms));
    }

    public void stop(MasterServer ms) {
        masterServerTaskQueue.start(new MasterStopTask(ms));
    }

    public void terminate(MasterServer ms, boolean force) {
        masterServerTaskQueue.start(new MasterTerminateTask(ms, force));
    }

    public void cloneTemplateFromSource(MasterTemplate mt) {
        masterServerTaskQueue.start(new TemplateCloneTask(mt));
    }

    private void process() throws Exception {
        // It is not possible to hold onto the reference as the label will be removed from Hudson
        // on a configuration change when labels are trimmed and if the  label does not contain
        // any nodes or clouds. Once removed the label will not be updated if/when new clouds/nodes are
        // added.
        masterLabel = getLabel();

        // Process master tasks
        masterServerTaskQueue.process();

        // Process node tasks
        nodeTaskQueue.process();

        // Clean up the node/masters map
        for (PlannedMasterRequest pmr : pendingMasterRequests) {
            // If re-provisioning from a state that set the node
            // This can happen for a provisioning error, we don't want to remove information when a provisioning
            // error occurs as we may need to resolve the error on the provisioning node
            final Node n = pmr.ms.getNode();
            if (n != null && nodesWithMasters.containsKey(n)) {
                nodesWithMasters.get(n).remove(pmr.ms);
            }
        }

        // Take a copy of the planned master requests to ignore any requests added while processing
        // TODO this could be achieve by a forwarding queue impl that forwards all modification requests to the delegate
        // but defers read requests to the copy
        currentMasterRequests = Lists.newArrayList(pendingMasterRequests);

        provision();
        terminateNodes();
    }

    // Provisioning

    private final class PlannedMasterRequest {
        private final Semaphore acquiredState = new Semaphore(1);

        private final MasterServer ms;
        private final URL metaNectarEndpoint;
        private final Map<String, Object> properties;
        private final boolean start;

        public PlannedMasterRequest(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties, boolean start) {
            this.ms = ms;
            this.metaNectarEndpoint = metaNectarEndpoint;
            this.properties = properties;
            this.start = start;
        }

        private boolean tryAcquire() {
            return acquiredState.tryAcquire();
        }

        public boolean tryCancel() throws IOException {
            if (!tryAcquire())
                return false;

            pendingMasterRequests.remove(this);
            ms.cancelPreProvisionState();
            return true;
        }

        public boolean tryProcess(final Node n) throws Exception {
            if (!tryAcquire())
                return false;

            final int id = MasterServer.NODE_IDENTIFIER_FINDER.getUnusedIdentifier(nodesWithMasters.get(n));
            final MasterProvisionTask mpt = (start)
                    ? new MasterProvisionThenStartTask(ms, metaNectarEndpoint, properties, n, id)
                    : new MasterProvisionTask(ms, metaNectarEndpoint, properties, n, id);

            mpt.start();
            masterServerTaskQueue.getQueue().add(mpt);

            return true;
        }
    }

    private final TaskQueue<NodeProvisionTask> nodeTaskQueue = new TaskQueue<NodeProvisionTask>();

    private final MasterServerTaskQueue masterServerTaskQueue = new MasterServerTaskQueue();

    private final ConcurrentLinkedQueue<PlannedMasterRequest> pendingMasterRequests = new ConcurrentLinkedQueue<PlannedMasterRequest>();

    private Collection<PlannedMasterRequest> currentMasterRequests;

    private void provision() throws Exception {
        provisionMasterRequests();
        provisionFromCloud();
    }

    private void provisionMasterRequests() throws Exception {
        // Check masters nodes to see if a new master can be provisioned on an existing masters node
        if (currentMasterRequests.isEmpty())
            return;

        for (Node n : masterLabel.getNodes()) {
            // TODO should the offline cause be checked, should an attempt be made go online if not marked
            // as temporarily offline?
            if (n.toComputer().isOnline()) {

                // TODO check if masters are already provisioned, if so this means a re-provision.
                //
                final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(n);
                final int freeSlots = p.getMaxMasters() - nodesWithMasters.get(n).size();
                if (freeSlots < 1)
                    continue;

                for (Iterator<PlannedMasterRequest> itr = Iterators.limit(currentMasterRequests.iterator(), freeSlots); itr.hasNext();) {
                    final PlannedMasterRequest pmr = itr.next();

                    try {
                        // Ignore request if advanced from the pre-provisioning state
                        if (pmr.ms.getState().ordinal() > MasterServer.State.PreProvisioning.ordinal() &&
                                pmr.ms.getState() != MasterServer.State.ProvisioningErrorNoResources) {
                            continue;
                        }

                        pmr.tryProcess(n);
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        pendingMasterRequests.remove(pmr);
                        itr.remove();
                    }
                }
            }
        }
    }

    private void provisionFromCloud() throws Exception {
        // TODO capable nodes could be offline, meaning there are still pending planned master requests
        // Need to be careful provisioning more nodes in such cases.

        // If there are still pending requests and no pending nodes
        if (currentMasterRequests.size() > 0 && nodeTaskQueue.getQueue().isEmpty()) {
            // Check clouds to see if a new masters node can be provisioned
            Collection<PlannedNode> pns = Collections.emptySet();
            for (Cloud c : masterLabel.getClouds()) {
                pns = c.provision(masterLabel, 1);
                if (pns == null)
                    pns = Collections.emptySet();

                if (!pns.isEmpty()) {
                    for (PlannedNode pn : pns) {
                        try {
                            NodeProvisionThenOnlineTask npt = new NodeProvisionThenOnlineTask(mn, c, pn);
                            npt.start();
                            nodeTaskQueue.add(npt);
                        } catch (Exception e) {
                            // Ignore
                        }
                    }

                    break;
                }
            }

            if (pns.isEmpty()) {
                for (PlannedMasterRequest pmr : currentMasterRequests) {
                    pmr.ms.setProvisionErrorNoResourcesState();
                    LOGGER.log(Level.WARNING, "No resources to provision master " + pmr.ms.getName());
                }
            }
        }
    }


    // Terminating

    private void terminateNodes() throws Exception {
        // If there are no pending requests to provision masters or provision masters nodes
        // then check if nodes with no provisioned masters can be terminated
        if (pendingMasterRequests.isEmpty() && nodeTaskQueue.getQueue().isEmpty()) {
            // Reap nodes with no provisioned masters
            for (Node n : masterLabel.getNodes()) {
                if (!nodesWithMasters.containsKey(n) || nodesWithMasters.get(n).isEmpty()) {
                    final Computer c = n.toComputer();

                    if (c.getRetentionStrategy() instanceof NodeTerminatingRetentionStrategy) {
                        final NodeTerminatingRetentionStrategy rs = (NodeTerminatingRetentionStrategy)c.getRetentionStrategy();

                        try {
                            // TODO terminate should be added as an async task
                            rs.terminate(n);

                            LOGGER.info("Terminate completed for node " + n.getNodeName());

                            MasterProvisioningCloudListener.fireOnTerminated(n);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Terminate error for node " + n.getNodeName());

                            MasterProvisioningCloudListener.fireOnTerminatingError(n, e);
                        }
                    }
                }
            }
        }
    }


    @Extension
    public static class MasterProvisionerInvoker extends PeriodicWork {
        public static int INITIALDELAY = Integer.getInteger(MasterProvisioner.class.getName()+".initialDelay", LoadStatistics.CLOCK*10);

        public static int RECURRENCEPERIOD = Integer.getInteger(MasterProvisioner.class.getName()+".recurrencePeriod",LoadStatistics.CLOCK);

        @Override
        public long getInitialDelay() {
            return INITIALDELAY;
        }

        public long getRecurrencePeriod() {
            return RECURRENCEPERIOD;
        }

        @Override
        protected void doRun() {
            MetaNectar mn = MetaNectar.getInstance();
            if (mn.masterProvisioner == null)
                return;

            try {
                mn.masterProvisioner.process();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error when master provisioning or terminating", e);
            }
        }
    }
}
