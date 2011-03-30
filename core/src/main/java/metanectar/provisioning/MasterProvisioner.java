package metanectar.provisioning;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
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
 *
 * @author Paul Sandoz
 */
public class MasterProvisioner {

    private static final Logger LOGGER = Logger.getLogger(MasterProvisioner.class.getName());

    public static interface MasterListener {
        void onProvisioningMaster(String organization, Node n);

        void onErrorProvisioningMaster(String organization, Node n, Throwable error);


        void onProvisionedMaster(Master m, Node n);

        void onErrorProvisionedMaster(String organization, Node n, Throwable error);


        void onUnprovisionedMaster(Master m, Node n);
    }

    public static abstract class Master {
        public final String organization;
        public final URL endpoint;

        public Master(String organization, URL endpoint) {
            this.organization = organization;
            this.endpoint = endpoint;
        }

        public abstract Future<?> stop();
    }

    private static final class PlannedMaster {
        public final MasterListener ml;
        public final String organization;
        public final Node node;
        public final java.util.concurrent.Future<Master> future;

        public PlannedMaster(MasterListener ml, String organization, Node node, Future<Master> future) {
            this.ml = ml;
            this.organization = organization;
            this.node=node;
            this.future = future;
        }
    }

    private static final class PlannedMasterRequest {
        public final MasterListener ml;
        public final MasterProvisioningService mns;
        public final String organization; 
        public final URL metaNectarEndpoint; 
        public final String key;
        
        public PlannedMasterRequest(MasterListener ml, MasterProvisioningService mns, String organization, URL metaNectarEndpoint, String key) {
            this.ml = ml;
            this.mns = mns;
            this.organization = organization;
            this.metaNectarEndpoint = metaNectarEndpoint;
            this.key = key;
        }

        public PlannedMaster toPlannedMaster(Node node, Future<Master> future) {
            return new PlannedMaster(ml, organization, node, future);
        }
    }

    // TODO make configurable
    public final Label masterLabel = MetaNectar.getInstance().getLabel("_masters_");

    private Multimap<Node, Master> masters = ArrayListMultimap.create();

    private Multimap<Node, PlannedMaster> pendingPlannedMasters = ArrayListMultimap.create();

    private List<PlannedMasterRequest> pendingPlannedMasterRequests = Collections.synchronizedList(new ArrayList<PlannedMasterRequest>());

    private List<PlannedNode> pendingPlannedNodes = new ArrayList<PlannedNode>();

    // TODO this should be configurable per node.
    private static final int MAX_MASTERS = 4;

    private void update() {
        Hudson hudson = Hudson.getInstance();

        // Process pending planned masters
        for (Iterator<PlannedMaster> itr = pendingPlannedMasters.values().iterator(); itr.hasNext();) {
            final PlannedMaster pm = itr.next();
            if (pm.future.isDone()) {
                try {
                    Master m = pm.future.get();
                    masters.put(pm.node, m);

                    LOGGER.info(pm.organization +" master provisioned");

                    pm.ml.onProvisionedMaster(m, pm.node);
                } catch (InterruptedException e) {
                    throw new AssertionError(e); // since we confirmed that the future is already done
                } catch (ExecutionException e) {
                    LOGGER.log(Level.WARNING, "Provisioned master "+pm.organization +" failed to launch",e);
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

                // TODO work out free slots

                for (Iterator<PlannedMasterRequest> itr = pendingPlannedMasterRequests.iterator(); itr.hasNext();) {
                    if ((masters.get(n).size() + pendingPlannedMasters.get(n).size()) < MAX_MASTERS) {
                        final PlannedMasterRequest pmr = itr.next();
                        try {
                            Future<Master> f = pmr.mns.provision(n.toComputer().getChannel(), pmr.organization, pmr.metaNectarEndpoint, pmr.key);
                            pendingPlannedMasters.put(n, pmr.toPlannedMaster(n, f));

                            LOGGER.info(pmr.organization +" master provisioning started");

                            pmr.ml.onProvisioningMaster(pmr.organization, n);
                        } catch (InterruptedException e) {
                            LOGGER.log(Level.WARNING, "Provisioned masters node "+pmr.organization+" failed to launch",e);
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Provisioned masters node "+pmr.organization+" failed to launch",e);
                        } finally {
                            itr.remove();
                        }
                    } else {
                        break;
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
            Hudson h = Hudson.getInstance();
            MetaNectar mn = MetaNectar.getInstance();
            mn.masterProvisioner.update();
        }
    }

    public Multimap<Node, Master> getProvisionedMasters() {
        return masters;
    }

    public void provision(MasterListener ml, MasterProvisioningService mns, String organization, URL metaNectarEndpoint, String key) {
        pendingPlannedMasterRequests.add(new PlannedMasterRequest(ml, mns, organization, metaNectarEndpoint, key));
    }

    void delete(String organization) {
        // TODO
    }
}
