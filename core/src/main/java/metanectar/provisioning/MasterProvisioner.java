package metanectar.provisioning;

import com.google.common.collect.*;
import hudson.Extension;
import hudson.model.*;
import hudson.slaves.Cloud;
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
 * - store state on a NodeProperty
 *   - max number of masters that can be provisioned
 *   - list of masters that are provisioned
 *   - master provisioning service class for factory?
 * -
 *
 * @author Paul Sandoz
 */
public class MasterProvisioner {

    private static final Logger LOGGER = Logger.getLogger(MasterProvisioner.class.getName());

    public static interface MasterProvisionListener {
        void onProvisionStarted(String organization, Node n);

        void onProvisionStartedError(String organization, Node n, Throwable error);

        void onProvisionCompleted(Master m, Node n);

        void onProvisionCompletedError(String organization, Node n, Throwable error);
    }

    public static interface MasterTerminateListener {
        public void onTerminateStarted(Master m, Node n);

        public void onTerminateCompleted(Master m, Node n);

        public void onTerminateError(Master m, Node n, Throwable e);
    }

    private static final class PlannedMaster {
        public final MasterProvisionListener ml;
        public final String organization;
        public final Node node;
        public final java.util.concurrent.Future<Master> future;

        public PlannedMaster(MasterProvisionListener ml, String organization, Node node, Future<Master> future) {
            this.ml = ml;
            this.organization = organization;
            this.node=node;
            this.future = future;
        }
    }

    private static final class PlannedMasterRequest {
        public final MasterProvisionListener ml;
        public final String organization;
        public final URL metaNectarEndpoint; 
        public final Map<String, String> properties;
        
        public PlannedMasterRequest(MasterProvisionListener ml, String organization, URL metaNectarEndpoint, Map<String, String> properties) {
            this.ml = ml;
            this.organization = organization;
            this.metaNectarEndpoint = metaNectarEndpoint;
            this.properties = properties;
        }

        public PlannedMaster toPlannedMaster(Node node, Future<Master> future) {
            return new PlannedMaster(ml, organization, node, future);
        }
    }

    // TODO make configurable
    public final Label masterLabel = MetaNectar.getInstance().getLabel("_masters_");

    // TODO should this be a weak hash map?
    private Multimap<Node, PlannedMaster> pendingPlannedMasters = ArrayListMultimap.create();

    private List<PlannedMasterRequest> pendingPlannedMasterRequests = Collections.synchronizedList(new ArrayList<PlannedMasterRequest>());

    private List<PlannedNode> pendingPlannedNodes = new ArrayList<PlannedNode>();

    private void provision() {
        Hudson hudson = Hudson.getInstance();

        // Process pending planned masters
        for (Iterator<PlannedMaster> itr = pendingPlannedMasters.values().iterator(); itr.hasNext();) {
            final PlannedMaster pm = itr.next();
            if (pm.future.isDone()) {
                try {
                    Master m = pm.future.get();
                    MasterProvisioningNodeProperty.get(pm.node).provision(m);

                    LOGGER.info(pm.organization +" master provisioned");

                    pm.ml.onProvisionCompleted(m, pm.node);
                } catch (InterruptedException e) {
                    throw new AssertionError(e); // since we confirmed that the future is already done
                } catch (ExecutionException e) {
                    LOGGER.log(Level.WARNING, "Provisioned master "+pm.organization +" failed to launch",e.getCause());
                } finally {
                    itr.remove();
                }
            }
        }

        // Process pending planned masters nodes
        for (Iterator<PlannedNode> itr = pendingPlannedNodes.iterator(); itr.hasNext();) {
            final PlannedNode pn = itr.next();
            if (pn.future.isDone()) {
                try {
                    hudson.addNode(pn.future.get());

                    // TODO listener for provisioned node? or reuse existing listener?
                    LOGGER.info(pn.displayName+" provisioning successfully completed. We have now "+hudson.getComputers().length+" computer(s)");
                } catch (InterruptedException e) {
                    throw new AssertionError(e); // since we confirmed that the future is already done
                } catch (ExecutionException e) {
                    LOGGER.log(Level.WARNING, "Provisioned masters node "+pn.displayName+" failed to launch",e.getCause());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Provisioned masters node "+pn.displayName+" failed to launch",e);
                } finally {
                    itr.remove();
                }
            }
        }

        // Check masters nodes to see if a new master can be provisioned on an existing masters node
        for (Node n : masterLabel.getNodes()) {
            if (n.toComputer().isOnline()) {

                final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(n);
                final int freeSlots = p.getMaxMasters() - (p.getProvisioned().size() + pendingPlannedMasters.get(n).size());
                for (Iterator<PlannedMasterRequest> itr = Iterators.limit(pendingPlannedMasterRequests.iterator(), freeSlots); itr.hasNext();) {
                    final PlannedMasterRequest pmr = itr.next();
                    try {
                        Future<Master> f = p.getProvisioningService().provision(n.toComputer().getChannel(), pmr.organization, pmr.metaNectarEndpoint, pmr.properties);
                        pendingPlannedMasters.put(n, pmr.toPlannedMaster(n, f));

                        LOGGER.info(pmr.organization +" master provisioning started");

                        pmr.ml.onProvisionStarted(pmr.organization, n);
                    } catch (InterruptedException e) {
                        LOGGER.log(Level.WARNING, "Provisioned masters node "+pmr.organization+" failed to launch",e);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Provisioned masters node "+pmr.organization+" failed to launch",e);
                    } finally {
                        itr.remove();
                    }
                }
            }
        }

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
                    LOGGER.log(Level.WARNING, "No resources to provision master" + pmr.organization);
                }
            }
        }
    }


    private static final class TerminateMasterRequest {
        public final MasterTerminateListener ml;
        public final String organization;
        public final boolean clean;

        public TerminateMasterRequest(MasterTerminateListener ml, String organization, boolean clean) {
            this.ml = ml;
            this.organization = organization;
            this.clean = clean;
        }

        public TerminateMaster toDeleteMaster(Map.Entry<Node, Master> node, Future<?> future) {
            return new TerminateMaster(ml, organization, node, future);
        }
    }

    private static final class TerminateMaster {
        public final MasterTerminateListener ml;
        public final String organization;
        Map.Entry<Node, Master> nodeMaster;
        public final java.util.concurrent.Future<?> future;

        public TerminateMaster(MasterTerminateListener ml, String organization, Map.Entry<Node, Master> nodeMaster, Future<?> future) {
            this.ml = ml;
            this.organization = organization;
            this.nodeMaster = nodeMaster;
            this.future = future;
        }
    }

    private List<TerminateMasterRequest> pendingTerminateMasterRequests = Collections.synchronizedList(new ArrayList<TerminateMasterRequest>());

    private List<TerminateMaster> pendingTerminateMasters = Collections.synchronizedList(new ArrayList<TerminateMaster>());

    void delete() {
        // Process pending delete masters
        for (Iterator<TerminateMaster> itr = pendingTerminateMasters.iterator(); itr.hasNext();) {
            final TerminateMaster dm = itr.next();
            if (dm.future.isDone()) {
                try {
                    dm.future.get();
                    MasterProvisioningNodeProperty.get(dm.nodeMaster.getKey()).terminate(dm.nodeMaster.getValue());

                    dm.ml.onTerminateCompleted(dm.nodeMaster.getValue(), dm.nodeMaster.getKey());

                    LOGGER.info(dm.organization +" master deleted");
                } catch (InterruptedException e) {
                    throw new AssertionError(e); // since we confirmed that the future is already done
                } catch (ExecutionException e) {
                    LOGGER.log(Level.WARNING, "Deletion of master "+dm.organization +" failed",e);
                } finally {
                    // TODO should we remote the master from the node on failure?
                    itr.remove();
                }
            }
        }

        // Process pending delete master requests
        for (Iterator<TerminateMasterRequest> itr = pendingTerminateMasterRequests.iterator(); itr.hasNext();) {
            final TerminateMasterRequest dmr = itr.next();
            try {
                final Map.Entry<Node, Master> nodeMaster = getNodeAndMasterFromOrg(dmr.organization);
                if (nodeMaster == null) {
                    // Ignore if there is no provisioned master for the organization
                    continue;
                }

                final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(nodeMaster.getKey());
                final Future<?> f = p.getProvisioningService().terminate(nodeMaster.getKey().toComputer().getChannel(), dmr.organization, dmr.clean);
                pendingTerminateMasters.add(dmr.toDeleteMaster(nodeMaster, f));

                dmr.ml.onTerminateStarted(nodeMaster.getValue(), nodeMaster.getKey());

                LOGGER.info(dmr.organization +" master deletion started");
            } catch (InterruptedException e) {
                throw new AssertionError(e); // since we confirmed that the future is already done
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Deletion of master "+dmr.organization+" failed",e);
            } finally {
                // TODO should we remote the master from the node on failure?
                itr.remove();
            }
        }


        // If there are no pending requests to provision masters or provision masters nodes
        // then check if nodes with no provisioned masters can be terminated
        if (pendingPlannedMasterRequests.size() == 0 && pendingPlannedNodes.size() == 0) {
            // Reap nodes with no provisioned masters
            for (Node n : masterLabel.getNodes()) {
                if (MasterProvisioningNodeProperty.get(n).getProvisioned().isEmpty()) {
                    // TODO how do we determine if this node can be terminated, e.g. was provisioned from the Cloud
                }
            }
        }
    }

    private Map.Entry<Node, Master> getNodeAndMasterFromOrg(String organization) {
        for (Node n : masterLabel.getNodes()) {
            final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(n);

            for (Master m : p.getProvisioned()) {
                if (m.organization.equals(organization)) {
                    return Maps.immutableEntry(n, m);
                }
            }
        }
        return null;
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

            mn.masterProvisioner.provision();
            mn.masterProvisioner.delete();
        }
    }

    public Multimap<Node, Master> getProvisionedMasters() {
        Multimap<Node, Master> masters = HashMultimap.create();

        for (Node n : masterLabel.getNodes()) {
            final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(n);

            masters.putAll(n, p.getProvisioned());
        }
        return masters;
    }

    public void provision(MasterProvisionListener ml, String organization, URL metaNectarEndpoint,
                          Map<String, String> properties) {
        pendingPlannedMasterRequests.add(new PlannedMasterRequest(ml, organization, metaNectarEndpoint, properties));
    }

    void terminate(MasterTerminateListener ml, String organization, boolean clean) {
        pendingTerminateMasterRequests.add(new TerminateMasterRequest(ml, organization, clean));
    }
}
