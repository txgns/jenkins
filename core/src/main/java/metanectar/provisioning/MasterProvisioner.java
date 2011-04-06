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

    private static final Logger LOGGER = Logger.getLogger(MasterProvisioner.class.getName());

    public static interface MasterProvisionListener {
        void onProvisionStarted(MasterServer ms, Node n);

        void onProvisionStartedError(MasterServer ms, Node n, Throwable error);

        void onProvisionCompleted(MasterServer ms);

        void onProvisionCompletedError(MasterServer ms, Throwable error);
    }

    public static interface MasterTerminateListener {
        public void onTerminateStarted(MasterServer ms);

        public void onTerminateCompleted(MasterServer ms, Node n);

        public void onTerminateError(MasterServer ms, Node n, Throwable e);
    }

    private static final class PlannedMaster {
        public final MasterProvisionListener ml;
        public final MasterServer ms;
        public final Node node;
        public final java.util.concurrent.Future<Master> future;

        public PlannedMaster(MasterProvisionListener ml, MasterServer ms, Node node, Future<Master> future) {
            this.ml = ml;
            this.ms = ms;
            this.node=node;
            this.future = future;
        }
    }

    private static final class PlannedMasterRequest {
        public final MasterProvisionListener ml;
        public final MasterServer ms;
        public final URL metaNectarEndpoint; 
        public final Map<String, String> properties;
        
        public PlannedMasterRequest(MasterProvisionListener ml, MasterServer ms, URL metaNectarEndpoint, Map<String, String> properties) {
            this.ml = ml;
            this.ms = ms;
            this.metaNectarEndpoint = metaNectarEndpoint;
            this.properties = properties;
        }

        public PlannedMaster toPlannedMaster(Node node, Future<Master> future) {
            return new PlannedMaster(ml, ms, node, future);
        }
    }

    // TODO make configurable
    public final Label masterLabel = MetaNectar.getInstance().getLabel("_masters_");

    private Multimap<Node, PlannedMaster> pendingPlannedMasters = ArrayListMultimap.create();

    private List<PlannedMasterRequest> pendingPlannedMasterRequests = Collections.synchronizedList(new ArrayList<PlannedMasterRequest>());

    private List<PlannedNode> pendingPlannedNodes = new ArrayList<PlannedNode>();

    // TODO check is a master is already provisioned or to be provisioned

    // TODO check if cannot provision a master because there are no nodes/clouds configured

    private Multimap<Node, MasterServer> provision(MetaNectar mn) {
        Hudson hudson = Hudson.getInstance();

        // Process pending planned masters
        for (Iterator<PlannedMaster> itr = pendingPlannedMasters.values().iterator(); itr.hasNext();) {
            final PlannedMaster pm = itr.next();
            if (pm.future.isDone()) {
                try {
                    final Master m = pm.future.get();

                    // Set the provisioned state on the master server
                    final MasterServer ms = mn.getMasterByOrganization(m.organization);
                    ms.setProvisionedState(pm.node, m.endpoint);

                    LOGGER.info(ms.getName() +" master provisioned");

                    pm.ml.onProvisionCompleted(ms);
                } catch (InterruptedException e) {
                    throw new AssertionError(e); // since we confirmed that the future is already done
                } catch (ExecutionException e) {
                    LOGGER.log(Level.WARNING, "Provisioned master " + pm.ms.getName() +" failed to launch",e.getCause());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Provisioned masters" + pm.ms.getName() + " failed to launch",e);
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
        final Multimap<Node, MasterServer> provisioned = MasterProvisioner.getProvisionedMasters(mn);
        for (Node n : masterLabel.getNodes()) {
            if (n.toComputer().isOnline()) {

                final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(n);
                final int freeSlots = p.getMaxMasters() - (provisioned.get(n).size() + pendingPlannedMasters.get(n).size());
                for (Iterator<PlannedMasterRequest> itr = Iterators.limit(pendingPlannedMasterRequests.iterator(), freeSlots); itr.hasNext();) {
                    final PlannedMasterRequest pmr = itr.next();
                    try {
                        Future<Master> f = p.getProvisioningService().provision(n.toComputer().getChannel(), pmr.ms.getName(), pmr.metaNectarEndpoint, pmr.properties);
                        pendingPlannedMasters.put(n, pmr.toPlannedMaster(n, f));

                        LOGGER.info(pmr.ms.getName() +" master provisioning started");

                        // Set the provisioning state on the master server
                        pmr.ms.setProvisioningState();

                        pmr.ml.onProvisionStarted(pmr.ms, n);
                    } catch (InterruptedException e) {
                        LOGGER.log(Level.WARNING, "Provisioned masters node "+pmr.ms.getName()+" failed to launch",e);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Provisioned masters node "+pmr.ms.getName()+" failed to launch",e);
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
                    LOGGER.log(Level.WARNING, "No resources to provision master" + pmr.ms.getName());
                }
            }
        }

        return provisioned;
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

        public TerminateMaster toDeleteMaster(MasterServer ms, Future<?> future) {
            return new TerminateMaster(ml, organization, ms, future);
        }
    }

    private static final class TerminateMaster {
        public final MasterTerminateListener ml;
        public final String organization;
        public final MasterServer ms;
        public final java.util.concurrent.Future<?> future;

        public TerminateMaster(MasterTerminateListener ml, String organization, MasterServer ms, Future<?> future) {
            this.ml = ml;
            this.organization = organization;
            this.ms = ms;
            this.future = future;
        }
    }

    private List<TerminateMasterRequest> pendingTerminateMasterRequests = Collections.synchronizedList(new ArrayList<TerminateMasterRequest>());

    private List<TerminateMaster> pendingTerminateMasters = Collections.synchronizedList(new ArrayList<TerminateMaster>());

    void delete(MetaNectar mn, Multimap<Node, MasterServer> provisioned) {
        // Process pending delete masters
        for (Iterator<TerminateMaster> itr = pendingTerminateMasters.iterator(); itr.hasNext();) {
            final TerminateMaster dm = itr.next();
            if (dm.future.isDone()) {
                try {
                    dm.future.get();

                    // Set terminated state on the master server
                    final Node n = dm.ms.getNode();
                    dm.ms.setTerminatedState();

                    dm.ml.onTerminateCompleted(dm.ms, n);

                    LOGGER.info(dm.organization +" master deleted");
                } catch (InterruptedException e) {
                    throw new AssertionError(e); // since we confirmed that the future is already done
                } catch (ExecutionException e) {
                    LOGGER.log(Level.WARNING, "Deletion of master "+dm.organization +" failed",e);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Deletion of master "+dm.organization+" failed",e);
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
                final MasterServer ms = mn.getMasterByOrganization(dmr.organization);
                if (ms == null) {
                    // Ignore if there is no provisioned master for the organization
                    continue;
                }

                final Node n = ms.getNode();
                final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(n);
                final Future<?> f = p.getProvisioningService().terminate(n.toComputer().getChannel(), dmr.organization, dmr.clean);
                pendingTerminateMasters.add(dmr.toDeleteMaster(ms, f));

                dmr.ml.onTerminateStarted(ms);

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

            Multimap<Node, MasterServer> provisioned = mn.masterProvisioner.provision(mn);
            mn.masterProvisioner.delete(mn, provisioned);
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

    public void provision(MasterProvisionListener ml, MasterServer ms, URL metaNectarEndpoint,
                          Map<String, String> properties) {
        pendingPlannedMasterRequests.add(new PlannedMasterRequest(ml, ms, metaNectarEndpoint, properties));
    }

    void terminate(MasterTerminateListener ml, String organization, boolean clean) {
        pendingTerminateMasterRequests.add(new TerminateMasterRequest(ml, organization, clean));
    }
}
