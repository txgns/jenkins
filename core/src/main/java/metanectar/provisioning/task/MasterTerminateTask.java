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
        final Node node = ms.getNode();

        try {
            final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(node);
            this.future = p.getProvisioningService().terminate(
                    node.toComputer().getChannel(), ms.getTaskListener(),
                    ms.getName(), false);

            LOGGER.info("Terminating master " + ms.getName() + " on node " + node.getNodeName());

            // Set terminate stated state on the master server
            ms.setTerminateStartedState();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Terminating error for master " + ms.getName() + " on node " + node.getNodeName(), e);

            ms.setTerminateErrorState(e);
            throw e;
        }
    }

    public MasterServerTask end() throws Exception {
        final Node node = ms.getNode();

        try {
            future.get();

            LOGGER.info("Terminating completed for master " + ms.getName() + " on node " + node.getNodeName());

            // Set terminate completed state on the master server
            ms.setTerminateCompletedState();
        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Terminating completion error for master " + ms.getName() + " on node " + node.getNodeName(), cause);

            ms.setTerminateErrorState(cause);
            throw e;
        }

        return null;
    }
}
