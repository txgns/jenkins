package metanectar.provisioning.task;

import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import metanectar.cloud.MasterProvisioningCloudListener;
import metanectar.model.MetaNectar;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class NodeProvisionTask extends TaskWithFuture<Node, Task> {
    private static final Logger LOGGER = Logger.getLogger(NodeProvisionTask.class.getName());

    private final MetaNectar mn;

    private final Cloud c;

    private final NodeProvisioner.PlannedNode pn;

    private Node n;

    public NodeProvisionTask(long timeout, MetaNectar mn, Cloud c, NodeProvisioner.PlannedNode pn) {
        super(timeout);

        this.mn = mn;
        this.c = c;
        this.pn = pn;
    }

    protected Node getNode() {
        return n;
    }

    public void start() throws Exception {
        LOGGER.info("Provision started for node " + pn.displayName + " on cloud " + c.name);

        setFuture(pn.future);
        MasterProvisioningCloudListener.fireOnProvisioning(c);
    }

    public Task end() throws Exception {
        try {
            n = getFuture().get();
            mn.addNode(n);

            LOGGER.info("Provision completed for node " + pn.displayName + " on cloud " + c.name + ". There are now " + mn.getComputers().length + " computer(s)");

            MasterProvisioningCloudListener.fireOnProvisioned(c, n);
        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Provisioned completion error for node " + pn.displayName + " on cloud " + c.name, cause);

            MasterProvisioningCloudListener.fireOnProvisioningError(c, e);
            throw e;
        }

        return null;
    }
}