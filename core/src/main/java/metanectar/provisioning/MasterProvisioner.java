package metanectar.provisioning;

import com.google.common.collect.*;
import hudson.Extension;
import hudson.model.*;
import hudson.slaves.Cloud;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;

import static hudson.slaves.NodeProvisioner.PlannedNode;


import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final class PlannedMaster {
        public final MasterServer ms;
        public final Node node;
        public final java.util.concurrent.Future<Master> future;

        public PlannedMaster(MasterServer ms, Node node, Future<Master> future) {
            this.ms = ms;
            this.node=node;
            this.future = future;
        }
    }

    private static final class PlannedMasterRequest {
        public final MasterServer ms;
        public final URL metaNectarEndpoint; 
        public final Map<String, Object> properties;
        
        public PlannedMasterRequest(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties) {
            this.ms = ms;
            this.metaNectarEndpoint = metaNectarEndpoint;
            this.properties = properties;
        }

        public PlannedMaster toPlannedMaster(Node node, Future<Master> future) {
            return new PlannedMaster(ms, node, future);
        }
    }

    // TODO make configurable
    public final Label masterLabel = MetaNectar.getInstance().getLabel(MASTER_LABEL_ATOM_STRING);

    private Multimap<Node, PlannedMaster> pendingPlannedMasters = ArrayListMultimap.create();

    private List<PlannedMasterRequest> pendingPlannedMasterRequests = Collections.synchronizedList(new ArrayList<PlannedMasterRequest>());

    private List<PlannedNode> pendingPlannedNodes = new ArrayList<PlannedNode>();

    // TODO check is a master is already provisioned or to be provisioned

    // TODO check if cannot provision a master because there are no nodes/clouds configured

    private Multimap<Node, MasterServer> provision(MetaNectar mn) throws Exception {
        provisionMasters();
        provisionNodes(mn);
        final Multimap<Node, MasterServer> provisioned = provisionMasterRequests(mn);
        provisionFromCloud();
        return provisioned;
    }

    private void provisionMasters() throws Exception {
        // Process pending planned masters
        for (Iterator<PlannedMaster> itr = pendingPlannedMasters.values().iterator(); itr.hasNext();) {
            final PlannedMaster pm = itr.next();
            if (pm.future.isDone()) {
                try {
                    final Master m = pm.future.get();

                    // Set the provision completed state on the master server
                    pm.ms.setProvisionCompletedState(pm.node, m.endpoint);
                    LOGGER.info(pm.ms.getName() +" master provisioned");
                } catch (InterruptedException e) {
                    throw new AssertionError(e); // since we confirmed that the future is already done
                } catch (Exception e) {
                    final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
                    LOGGER.log(Level.WARNING, "Provisioned master " + pm.ms.getName() + " failed to launch", cause);

                    pm.ms.setProvisionErrorState(pm.node, cause);
                } finally {
                    itr.remove();
                }
            }
        }
    }

    private void provisionNodes(MetaNectar mn) {
        // Process pending planned masters nodes
        for (Iterator<PlannedNode> itr = pendingPlannedNodes.iterator(); itr.hasNext();) {
            final PlannedNode pn = itr.next();
            if (pn.future.isDone()) {
                try {
                    mn.addNode(pn.future.get());

                    // TODO listener node provision
                    LOGGER.info(pn.displayName+" provisioning successfully completed. There are now " + mn.getComputers().length + " computer(s)");
                } catch (InterruptedException e) {
                    throw new AssertionError(e); // since we confirmed that the future is already done
                } catch (Exception e) {
                    final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
                    LOGGER.log(Level.WARNING, "Provisioned masters node " + pn.displayName + " failed to launch", cause);

                    // TODO listener for node provision error
                } finally {
                    itr.remove();
                }
            }
        }

    }

    private Multimap<Node, MasterServer> provisionMasterRequests(MetaNectar mn) throws Exception {
        // Check masters nodes to see if a new master can be provisioned on an existing masters node
        final Multimap<Node, MasterServer> provisioned = MasterProvisioner.getProvisionedMasters(mn);
        for (Node n : masterLabel.getNodes()) {
            if (n.toComputer().isOnline()) {

                // TODO check if masters are already provisioned, if so this means a re-provision.
                //
                final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(n);
                final int freeSlots = p.getMaxMasters() - (provisioned.get(n).size() + pendingPlannedMasters.get(n).size());
                if (freeSlots < 0)
                    continue;

                // Create a copy of the requests to iterate over rather than synchronizing the loop
                final List<PlannedMasterRequest> copyOfPendingPlannedMasterRequests = synchronizedCopy(pendingPlannedMasterRequests);
                for (Iterator<PlannedMasterRequest> itr = Iterators.limit(copyOfPendingPlannedMasterRequests.iterator(), freeSlots); itr.hasNext();) {
                    final PlannedMasterRequest pmr = itr.next();
                    try {
                        // Ignore request if advanced from the pre-provisioning state
                        if (pmr.ms.getState().ordinal() > MasterServer.State.PreProvisioning.ordinal())
                            continue;

                        final int id = getFreeId(n, provisioned, pendingPlannedMasters);
                        final Future<Master> f = p.getProvisioningService().provision(
                                n.toComputer().getChannel(), pmr.ms.getTaskListener(),
                                id, pmr.ms.getName(), pmr.metaNectarEndpoint, pmr.properties);
                        pendingPlannedMasters.put(n, pmr.toPlannedMaster(n, f));

                        LOGGER.info(pmr.ms.getName() +" master provisioning started");

                        // Set the provision started state on the master server
                        pmr.ms.setProvisionStartedState(n, id);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Provisioned masters node " + pmr.ms.getName() + " failed to launch", e);

                        pmr.ms.setProvisionErrorState(n, e);
                    } finally {
                        pendingPlannedMasterRequests.remove(pmr);
                    }
                }
            }
        }

        return provisioned;
    }

    private void provisionFromCloud() throws Exception {
        // TODO capable nodes could be offline, meaning there are still pending planned master requests
        // Need to be careful provisioning more nodes in such cases.

        // If there are still pending requests and no pending nodes
        if (pendingPlannedMasterRequests.size() > 0 && pendingPlannedNodes.size() == 0) {
            // Check clouds to see if a new masters node can be provisioned
            Collection<PlannedNode> pns = null;
            for (Cloud c : masterLabel.getClouds()) {
                pns = c.provision(masterLabel, 1);
                if (pns.size() > 0) {
                    pendingPlannedNodes.addAll(pns);
                    for (PlannedNode pn : pns) {
                        LOGGER.info("Started provisioning "+pn.displayName+" from "+c.name);
                    }

                    /// TODO listener for provisioning nodes, or is there an existing listener that can be reused?
                    break;
                }
            }

            if (pns == null) {
                for (PlannedMasterRequest pmr : pendingPlannedMasterRequests) {
                    pmr.ms.setProvisionErrorNoResourcesState();
                    LOGGER.log(Level.WARNING, "No resources to provision master " + pmr.ms.getName());
                }
            }
        }
    }

    private int getFreeId(Node n,
                          Multimap<Node, MasterServer> provisioned,
                          Multimap<Node, PlannedMaster> provisioning) {
        final List<MasterServer> l = new ArrayList<MasterServer>(provisioned.get(n));
        for (PlannedMaster pm : provisioning.get(n)) {
            l.add(pm.ms);
        }

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

    private static final class TerminateMasterRequest {
        public final MasterServer ms;
        public final boolean clean;

        public TerminateMasterRequest(MasterServer ms, boolean clean) {
            this.ms = ms;
            this.clean = clean;
        }

        public TerminateMaster toTerminateMaster(Future<?> future) {
            return new TerminateMaster(ms, future);
        }
    }

    private static final class TerminateMaster {
        public final MasterServer ms;
        public final java.util.concurrent.Future<?> future;

        public TerminateMaster(MasterServer ms, Future<?> future) {
            this.ms = ms;
            this.future = future;
        }
    }

    private List<TerminateMasterRequest> pendingTerminateMasterRequests = Collections.synchronizedList(new ArrayList<TerminateMasterRequest>());

    private List<TerminateMaster> pendingTerminateMasters = new ArrayList<TerminateMaster>();

    void terminate(Multimap<Node, MasterServer> provisioned) throws Exception {
        terminateMasters();
        terminateMasterRequests();
        terminateNodes(provisioned);
    }

    private void terminateMasters() throws Exception {
        // Process pending terminate masters
        for (Iterator<TerminateMaster> itr = pendingTerminateMasters.iterator(); itr.hasNext();) {
            final TerminateMaster tm = itr.next();
            final Node n = tm.ms.getNode();

            if (tm.future.isDone()) {
                try {
                    tm.future.get();

                    // Set terminate completed state on the master server
                    tm.ms.setTerminateCompletedState();
                    LOGGER.info(tm.ms.getName() +" master terminated");
                } catch (InterruptedException e) {
                    throw new AssertionError(e); // since we confirmed that the future is already done
                } catch (Exception e) {
                    final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
                    LOGGER.log(Level.WARNING, "Termination of master " + tm.ms.getName() + " failed", cause);

                    tm.ms.setTerminateErrorState(cause);
                } finally {
                    // TODO should we remove the master from the node on failure?
                    itr.remove();
                }
            }
        }

    }

    private void terminateMasterRequests() throws Exception {
        // Create a copy of the requests to iterate over rather than synchronizing the loop
        final List<TerminateMasterRequest> copyOfPendingTerminateMasterRequests = synchronizedCopy(pendingTerminateMasterRequests);

        // Process pending terminate master requests
        for (final TerminateMasterRequest tmr : copyOfPendingTerminateMasterRequests) {
            final MasterServer ms = tmr.ms;
            final Node n = ms.getNode();

            try {
                // Ignore request if in the process of terminating or terminated
                if (ms.getState() == MasterServer.State.Terminating ||
                        ms.getState() == MasterServer.State.Terminated)
                    continue;

                final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(n);
                final Future<?> f = p.getProvisioningService().terminate(
                        n.toComputer().getChannel(), ms.getTaskListener(),
                        ms.getName(), tmr.clean);
                pendingTerminateMasters.add(tmr.toTerminateMaster(f));

                // Set terminate stated state on the master server
                ms.setTerminateStartedState();
                LOGGER.info(ms.getName() +" master termination started");
            } catch (InterruptedException e) {
                throw new AssertionError(e); // since we confirmed that the future is already done
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Termination of master "+ tmr.ms.getName() + " failed", e);

                tmr.ms.setTerminateErrorState(e);
            } finally {
                // TODO should we remove the master from the node on failure?
                pendingTerminateMasterRequests.remove(tmr);
            }
        }
    }

    private void terminateNodes(Multimap<Node, MasterServer> provisioned) throws Exception {
        // If there are no pending requests to provision masters or provision masters nodes
        // then check if nodes with no provisioned masters can be terminated
        if (pendingPlannedMasterRequests.size() == 0 && pendingPlannedNodes.size() == 0) {
            // Reap nodes with no provisioned masters
            for (Node n : masterLabel.getNodes()) {
                if (!provisioned.containsKey(n)) {
                    // TODO how do we determine if this node can be terminated, e.g. was provisioned from the Cloud
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
                Multimap<Node, MasterServer> provisioned = mn.masterProvisioner.provision(mn);
                mn.masterProvisioner.terminate(provisioned);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error when master provisioning or terminating", e);
            }
        }
    }

    public static Multimap<Node, MasterServer> getProvisionedMasters(MetaNectar mn) {
        Multimap<Node, MasterServer> masters = HashMultimap.create();

        for (MasterServer ms : mn.getItems(MasterServer.class)) {
            if (ms.getNode() != null) {
                masters.put(ms.getNode(), ms);
            }
        }

        return masters;
    }

    public void provision(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties) throws IOException {
        ms.setPreProvisionState();
        pendingPlannedMasterRequests.add(new PlannedMasterRequest(ms, metaNectarEndpoint, properties));
    }

    public void terminate(MasterServer ms, boolean clean) throws IOException {
        ms.setPreTerminateState();
        pendingTerminateMasterRequests.add(new TerminateMasterRequest(ms, clean));
    }

    private <T> List<T> synchronizedCopy(List<T> l) {
        synchronized (l) {
            return Lists.newArrayList(l);
        }
    }
}
