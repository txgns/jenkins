package metanectar.provisioning;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.*;
import hudson.Extension;
import hudson.model.*;
import hudson.slaves.Cloud;
import metanectar.cloud.MasterProvisioningCloud;
import metanectar.cloud.MasterProvisioningCloudProxy;
import metanectar.cloud.NodeTerminatingRetentionStrategy;
import metanectar.cloud.MasterProvisioningCloudListener;
import metanectar.model.MasterServer;
import metanectar.model.MasterServerListener;
import metanectar.model.MasterTemplate;
import metanectar.model.MetaNectar;
import metanectar.provisioning.task.*;
import metanectar.provisioning.task.TaskQueue;

import javax.annotation.Nullable;
import javax.swing.event.ListSelectionEvent;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
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

    private static final Logger LOGGER = Logger.getLogger(MasterProvisioner.class.getName());

    private final MetaNectar mn;

    private final long masterTimeout;

    private final long cloudTimout;

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

    public MasterProvisioner(MetaNectar mn, long masterTimeout, long cloudTimeout) {
        this.mn = mn;
        this.masterTimeout = masterTimeout;
        this.cloudTimout = cloudTimeout;

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

    public Future<MasterServer> provisionAndStart(MasterServer ms, URL metaNectarEndpoint) throws IOException {
        return provisionAndStart(ms, metaNectarEndpoint, new HashMap<String, Object>());
    }

    public Future<MasterServer> provisionAndStart(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties) throws IOException {
        ms.setPreProvisionState();
        final PlannedMasterRequest pmr = new PlannedMasterRequest(ms, metaNectarEndpoint, properties, true);
        pendingMasterRequests.add(pmr);
        return pmr.ft;
    }

    public Future<MasterServer> stopAndTerminate(MasterServer ms) {
        return masterServerTaskQueue.start(new MasterStopThenTerminateTask(masterTimeout, ms),
                FutureTaskEx.createValueFutureTask(ms));
    }

    public Future<MasterServer> provision(MasterServer ms, URL metaNectarEndpoint) throws IOException {
        return provision(ms, metaNectarEndpoint, new HashMap<String, Object>());
    }

    public Future<MasterServer> provision(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties) throws IOException {
        ms.setPreProvisionState();
        final PlannedMasterRequest pmr = new PlannedMasterRequest(ms, metaNectarEndpoint, properties, false);
        pendingMasterRequests.add(pmr);
        return pmr.ft;
    }

    public Future<MasterServer> reProvision(MasterServer ms, URL metaNectarEndpoint) {
        return reProvision(ms, metaNectarEndpoint, new HashMap<String, Object>());
    }

    public Future<MasterServer> reProvision(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties) {
        return masterServerTaskQueue.start(new MasterProvisionTask(masterTimeout, ms, metaNectarEndpoint, properties, ms.getNode(), ms.getNodeId()),
                FutureTaskEx.createValueFutureTask(ms));
    }

    public Future<MasterServer> start(MasterServer ms) {
        return masterServerTaskQueue.start(new MasterStartTask(masterTimeout, ms),
                FutureTaskEx.createValueFutureTask(ms));
    }

    public Future<MasterServer> stop(MasterServer ms) {
        return masterServerTaskQueue.start(new MasterStopTask(masterTimeout, ms),
                FutureTaskEx.createValueFutureTask(ms));
    }

    public Future<MasterServer> terminate(MasterServer ms, boolean force) {
        return masterServerTaskQueue.start(new MasterTerminateTask(masterTimeout, ms, force),
                FutureTaskEx.createValueFutureTask(ms));
    }

    public Future<MasterTemplate> cloneTemplateFromSource(MasterTemplate mt) {
        return masterServerTaskQueue.start(new TemplateCloneTask(masterTimeout, mt),
                FutureTaskEx.createValueFutureTask(mt));
    }

    private void process() throws Exception {
        // Process master tasks
        masterServerTaskQueue.process();

        // Process node tasks
        nodeTaskQueue.process();

        // Take a copy of the planned master requests to ignore any requests added while processing
        // TODO this could be achieve by a forwarding queue impl that forwards all modification requests to the delegate
        // but defers read requests to the copy
        currentMasterRequests = Lists.newLinkedList(pendingMasterRequests);

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
        private final FutureTaskEx<MasterServer> ft;

        public PlannedMasterRequest(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties, boolean start) {
            this.ms = ms;
            this.metaNectarEndpoint = metaNectarEndpoint;
            this.properties = properties;
            this.start = start;
            this.ft = FutureTaskEx.createValueFutureTask(ms);
        }

        private boolean tryAcquire() {
            return acquiredState.tryAcquire();
        }

        public boolean tryCancel() throws IOException {
            if (!tryAcquire())
                return false;

            pendingMasterRequests.remove(this);
            ms.cancelPreProvisionState();
            ft.cancel(true);
            return true;
        }

        public boolean tryProcess(final Node n) {
            if (!tryAcquire())
                return false;

            final int id = MasterServer.NODE_IDENTIFIER_FINDER.getUnusedIdentifier(nodesWithMasters.get(n));
            final MasterProvisionTask mpt = (start)
                    ? new MasterProvisionThenStartTask(masterTimeout, ms, metaNectarEndpoint, properties, n, id)
                    : new MasterProvisionTask(masterTimeout, ms, metaNectarEndpoint, properties, n, id);

            masterServerTaskQueue.start(mpt, ft);

            return true;
        }
    }

    private final TaskQueue<NodeProvisionTask> nodeTaskQueue = new TaskQueue<NodeProvisionTask>();

    private final MasterServerTaskQueue masterServerTaskQueue = new MasterServerTaskQueue();

    private final ConcurrentLinkedQueue<PlannedMasterRequest> pendingMasterRequests = new ConcurrentLinkedQueue<PlannedMasterRequest>();

    private Queue<PlannedMasterRequest> currentMasterRequests;

    /* package */ TaskQueue<NodeProvisionTask> getNodeTaskQueue() {
        return nodeTaskQueue;
    }

    /* package */ MasterServerTaskQueue getMasterServerTaskQueue() {
        return masterServerTaskQueue;
    }

    /* package */ ConcurrentLinkedQueue<PlannedMasterRequest> getPendingMasterRequests() {
        return pendingMasterRequests;
    }

    private void provision() throws Exception {
        provisionMasterRequests();
        provisionFromCloud();
    }

    private void provisionMasterRequests() throws Exception {
        // Check masters nodes to see if a new master can be provisioned on an existing masters node
        if (currentMasterRequests.isEmpty())
            return;

        for (final Node n : Iterables.concat(mn.getNodes(), Collections.singleton(mn))) {
            // TODO should the offline cause be checked, should an attempt be made go online if not marked
            // as temporarily offline?
            final Computer c = n.toComputer();
            if (c == null || c.isOffline())
                continue;

            // Ignore if node does not have a master provisioning node property
            final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(n);
            if (p == null)
                continue;

            final int freeSlots = p.geCapacityService().getFree(n, nodesWithMasters.get(n));
            if (freeSlots < 1)
                continue;

            for (Iterator<PlannedMasterRequest> itr = Iterators.limit(currentMasterRequests.iterator(), freeSlots); itr.hasNext();) {
                final PlannedMasterRequest pmr = itr.next();

                // Ignore if the label expression does not match the labels assigned to the node
                final Label label = pmr.ms.getLabel();
                if (label != null && !label.contains(n)) continue;

                pendingMasterRequests.remove(pmr);
                itr.remove();

                pmr.tryProcess(n);
            }
        }
    }

    private void provisionFromCloud() throws Exception {
        // TODO capable nodes could be offline, meaning there are still pending planned master requests
        // Need to be careful provisioning more nodes in such cases.

        // If there are still pending requests and no pending nodes
        if (currentMasterRequests.size() > 0 && nodeTaskQueue.getQueue().isEmpty()) {
            final Collection<PlannedMasterRequest> matched = provisionFromCloud(currentMasterRequests);

            final Collection<PlannedMasterRequest> unmatched = Collections2.filter(currentMasterRequests, new Predicate<PlannedMasterRequest>() {
                public boolean apply(@Nullable PlannedMasterRequest input) {
                    return !matched.contains(input);
                }
            });

            for (final PlannedMasterRequest pmr : unmatched) {
                if (pmr.ms.getState() != MasterServer.State.ProvisioningErrorNoResources) {
                    LOGGER.log(Level.WARNING, "No resources to provision master " + pmr.ms.getName());
                    pmr.ms.setProvisionErrorNoResourcesState();
                }
            }
        }
    }

    private Collection<PlannedMasterRequest> provisionFromCloud(final Iterable<PlannedMasterRequest> requests) {
        final Collection<PlannedMasterRequest> matched = Sets.newHashSet();

        final Multimap<Cloud, PlannedMasterRequest> matches = matchCloudsToRequests(requests);
        for (final Cloud c : matches.keySet()) {
            /*
             * Pass in null as the label. This assumes that the label assigned to a provisioned node will be
             * a default label based on what can be provisioned. Thus the node can still be used to provisioning
             * additional masters with a different matching label expression to those that are currently matched.
             */
            final Collection<PlannedNode> pns = c.provision(null, 1);

            if (pns != null && !pns.isEmpty()) {
                for (PlannedNode pn : pns) {
                    NodeProvisionThenOnlineTask npt = new NodeProvisionThenOnlineTask(cloudTimout, mn, c, pn);
                    nodeTaskQueue.start(npt);
                }
                matched.addAll(matches.get(c));
            }
        }

        return matched;
    }

    private Multimap<Cloud, PlannedMasterRequest> matchCloudsToRequests(final Iterable<PlannedMasterRequest> requests) {
        final Multimap<Cloud, PlannedMasterRequest> matches = LinkedListMultimap.create();

        for (final PlannedMasterRequest pmr : requests) {
            final Label label = pmr.ms.getLabel();

            for (final Cloud c : mn.clouds) {
                if (!(c instanceof MasterProvisioningCloudProxy))
                    continue;

                if (label == null || c.canProvision(label)) {
                    matches.put(c, pmr);
                }
            }
        }

        return matches;
    }

    // Terminating

    private void terminateNodes() throws Exception {
        // If there are no pending requests to provision masters or provision masters nodes
        // then check if nodes with no provisioned masters can be terminated
        if (pendingMasterRequests.isEmpty() && nodeTaskQueue.getQueue().isEmpty()) {
            // Reap nodes with no provisioned masters
            for (Node n : mn.getNodes()) {
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
