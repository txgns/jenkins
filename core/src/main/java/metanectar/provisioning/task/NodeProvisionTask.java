package metanectar.provisioning.task;

import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import metanectar.model.MetaNectar;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class NodeProvisionTask extends FutureTask<Node, Task> {
    private static final Logger LOGGER = Logger.getLogger(NodeProvisionTask.class.getName());

    private final MetaNectar mn;

    private final Cloud c;

    private final NodeProvisioner.PlannedNode pn;

    public NodeProvisionTask(MetaNectar mn, Cloud c, NodeProvisioner.PlannedNode pn) {
        super(pn.future);

        this.mn = mn;
        this.c = c;
        this.pn = pn;
    }

    public void start() throws Exception {
        LOGGER.info("Provision started for node " + pn.displayName + " on cloud " + c.name);
    }

    public Task end() throws Exception {
        try {
            mn.addNode(pn.future.get());

            // TODO listener node provision
            LOGGER.info("Provision completed for node " + pn.displayName + " on cloud " + c.name + ". There are now " + mn.getComputers().length + " computer(s)");
        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Provisioned completion error for node " + pn.displayName + " on cloud " + c.name, cause);

            // TODO listener for node provision error
            throw e;
        }

        return null;
    }
}