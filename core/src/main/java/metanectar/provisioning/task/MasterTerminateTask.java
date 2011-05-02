package metanectar.provisioning.task;

import hudson.model.Node;
import metanectar.model.MasterServer;
import metanectar.provisioning.MasterProvisioningNodeProperty;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class MasterTerminateTask extends MasterServerTask {
    private static final Logger LOGGER = Logger.getLogger(MasterTerminateTask.class.getName());

    public MasterTerminateTask(MasterServer ms) {
        super(ms);
    }

    public void start() throws Exception {
        try {
            final Node node = ms.getNode();
            final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(node);
            this.future = p.getProvisioningService().terminate(
                    node.toComputer().getChannel(), ms.getTaskListener(),
                    ms.getName(), false);

            LOGGER.info("Terminate started for master " + ms.getName() + " on node " + node.getNodeName());

            // Set terminate stated state on the master server
            ms.setTerminateStartedState();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Terminate starting error for master " + ms.getName() + " on node " + ms.getNode().getNodeName(), e);

            ms.setTerminateErrorState(e);
            throw e;
        }
    }

    public MasterServerTask end() throws Exception {
        try {
            future.get();

            LOGGER.info("Terminate completed for master " + ms.getName() + " on node " + ms.getNode().getNodeName());

            // Set terminate completed state on the master server
            ms.setTerminateCompletedState();
        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Termination completion error for master " + ms.getName() + " on node " + ms.getNode().getNodeName(), cause);

            ms.setTerminateErrorState(cause);
            throw e;
        }

        return null;
    }
}
