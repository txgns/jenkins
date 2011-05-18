package metanectar.provisioning;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import hudson.Extension;
import hudson.model.*;
import hudson.slaves.Cloud;
import metanectar.cloud.NodeTerminatingRetentionStrategy;
import metanectar.cloud.MasterProvisioningCloudListener;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;
import metanectar.provisioning.task.*;
import metanectar.provisioning.task.TaskQueue;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * <p>
 * TODO
 * - how to manage failures
 * - make provision requests idempotent?
 *
 * @author Paul Sandoz
 */
public class MasterProvisioner {

    public static final String MASTER_LABEL_ATOM_STRING = "_masters_";

    private static final Logger LOGGER = Logger.getLogger(MasterProvisioner.class.getName());

    private final MetaNectar mn;

    private Label masterLabel;

    // TODO check is a master is already provisioned or to be provisioned

    // TODO check if cannot provision a master because there are no nodes/clouds configured

    public MasterProvisioner(MetaNectar mn) {
        this.mn = mn;
    }

    public Label getLabel() {
        // TODO make configurable
        return MetaNectar.getInstance().getLabel(MASTER_LABEL_ATOM_STRING);
    }

    public boolean hasPendingRequests() {
        return !pendingPlannedMasterRequests.isEmpty();
    }

    public void provisionAndStart(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties) throws IOException {
        ms.setPreProvisionState();
        pendingPlannedMasterRequests.add(new PlannedMasterRequest(ms, metaNectarEndpoint, properties, true));
    }

    public void stopAndTerminate(MasterServer ms, boolean clean) {
        masterServerTaskQueue.start(new MasterStopThenTerminateTask(ms));
    }

    public void provision(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties) throws IOException {
        ms.setPreProvisionState();
        pendingPlannedMasterRequests.add(new PlannedMasterRequest(ms, metaNectarEndpoint, properties, false));
    }

    public void start(MasterServer ms) {
        masterServerTaskQueue.start(new MasterStartTask(ms));
    }

    public void stop(MasterServer ms) {
        masterServerTaskQueue.start(new MasterStopTask(ms));
    }

    public void terminate(MasterServer ms, boolean clean) {
        masterServerTaskQueue.start(new MasterTerminateTask(ms));
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

        Multimap<Node, MasterServer> provisioned = provision();
        terminateNodes(provisioned);
    }

    // Provisioning

    private static final class PlannedMasterRequest {
        public final MasterServer ms;
        public final URL metaNectarEndpoint;
        public final Map<String, Object> properties;
        public final boolean start;

        public PlannedMasterRequest(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties, boolean start) {
            this.ms = ms;
            this.metaNectarEndpoint = metaNectarEndpoint;
            this.properties = properties;
            this.start = start;
        }
    }

    private final MasterServerTaskQueue masterServerTaskQueue = new MasterServerTaskQueue();

    private final ConcurrentLinkedQueue<PlannedMasterRequest> pendingPlannedMasterRequests = new ConcurrentLinkedQueue<PlannedMasterRequest>();

    private final TaskQueue<NodeProvisionTask> nodeTaskQueue = new TaskQueue<NodeProvisionTask>();

    private Multimap<Node, MasterServer> provision() throws Exception {
        final Multimap<Node, MasterServer> provisioned = MasterProvisioner.getProvisionedMasters(mn);
        provisionMasterRequests(provisioned);
        provisionFromCloud();
        return provisioned;
    }

    private void provisionMasterRequests(Multimap<Node, MasterServer> provisioned) throws Exception {
        // Check masters nodes to see if a new master can be provisioned on an existing masters node
        if (pendingPlannedMasterRequests.isEmpty())
            return;

        for (Node n : masterLabel.getNodes()) {
            // TODO should the offline cause be checked, should an attempt be made go online if not marked
            // as temporarily offline?
            if (n.toComputer().isOnline()) {

                // TODO check if masters are already provisioned, if so this means a re-provision.
                //
                final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(n);
                final int freeSlots = p.getMaxMasters() - (provisioned.get(n).size() + masterServerTaskQueue.getProvisioning(n).size());
                if (freeSlots < 0)
                    continue;

                for (Iterator<PlannedMasterRequest> itr = Iterators.limit(pendingPlannedMasterRequests.iterator(), freeSlots); itr.hasNext();) {
                    final PlannedMasterRequest pmr = itr.next();

                    // Ignore request if advanced from the pre-provisioning state
                    if (pmr.ms.getState().ordinal() > MasterServer.State.PreProvisioning.ordinal() &&
                            pmr.ms.getState() != MasterServer.State.ProvisioningErrorNoResources) {
                        pendingPlannedMasterRequests.remove(pmr);
                        continue;
                    }

                    final int id = getFreeId(n, provisioned.get(n));
                    final MasterProvisionTask mpt = (pmr.start)
                            ? new MasterProvisionThenStartTask(pmr.ms, pmr.metaNectarEndpoint, pmr.properties, n, id)
                            : new MasterProvisionTask(pmr.ms, pmr.metaNectarEndpoint, pmr.properties, n, id);
                    try {
                        mpt.start();
                        masterServerTaskQueue.getQueue().add(mpt);
                    } catch (Exception e) {
                        // Ignore
                    } finally {
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
        if (pendingPlannedMasterRequests.size() > 0 && nodeTaskQueue.getQueue().isEmpty()) {
            // Check clouds to see if a new masters node can be provisioned
            Collection<PlannedNode> pns = Collections.emptySet();
            for (Cloud c : masterLabel.getClouds()) {
                pns = c.provision(masterLabel, 1);
                if (pns == null)
                    pns = Collections.emptySet();

                if (!pns.isEmpty()) {
                    for (PlannedNode pn : pns) {
                        try {
                            NodeProvisionTask npt = new NodeProvisionTask(mn, c, pn);
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
                for (PlannedMasterRequest pmr : pendingPlannedMasterRequests) {
                    pmr.ms.setProvisionErrorNoResourcesState();
                    LOGGER.log(Level.WARNING, "No resources to provision master " + pmr.ms.getName());
                }
            }
        }
    }

    private int getFreeId(Node n, Collection<MasterServer> provisioned) {
        final List<MasterServer> l = masterServerTaskQueue.getProvisioning(n);
        l.addAll(provisioned);

        return getFreeId(l);
    }

    private int getFreeId(final List<MasterServer> provisioned) {
        // Empty
        if (provisioned.isEmpty())
            return 0;

        // One Element
        if (provisioned.size() == 1) {
            final MasterServer ms = provisioned.get(0);
            return (ms.getId() > 0) ? 0 : ms.getId() + 1;
        }

        // Multiple elements, sort by ordinal then find a gap in the
        // intervals
        Collections.sort(provisioned, new Comparator<MasterServer>() {
            public int compare(MasterServer ms1, MasterServer ms2) {
                return ms1.getId() - ms2.getId();
            }
        });

        final Iterator<MasterServer> msi = provisioned.iterator();
        MasterServer start = msi.next();
        MasterServer end = null;
        while (msi.hasNext()) {
            end = msi.next();

            if (end.getId() - start.getId() > 1) {
                return start.getId() + 1;
            }

            start = end;
        }

        return end.getId() + 1;
    }

    // Terminating

    private void terminateNodes(Multimap<Node, MasterServer> provisioned) throws Exception {
        // If there are no pending requests to provision masters or provision masters nodes
        // then check if nodes with no provisioned masters can be terminated
        if (pendingPlannedMasterRequests.isEmpty() && nodeTaskQueue.getQueue().isEmpty()) {
            // Reap nodes with no provisioned masters
            for (Node n : masterLabel.getNodes()) {
                if (!masterServerTaskQueue.pendingTasksOnNode(n) && !provisioned.containsKey(n)) {
                    final Computer c = n.toComputer();

                    if (c.getRetentionStrategy() instanceof NodeTerminatingRetentionStrategy) {
                        final NodeTerminatingRetentionStrategy rs = (NodeTerminatingRetentionStrategy)c.getRetentionStrategy();

                        try {
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

    public static Multimap<Node, MasterServer> getProvisionedMasters(MetaNectar mn) {
        Multimap<Node, MasterServer> masters = HashMultimap.create();

        for (MasterServer ms : mn.getMasters()) {
            if (ms.getNode() != null) {
                masters.put(ms.getNode(), ms);
            }
        }

        return masters;
    }
}
