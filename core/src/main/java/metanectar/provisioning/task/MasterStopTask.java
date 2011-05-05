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
public class MasterStopTask extends MasterServerTask {
    private static final Logger LOGGER = Logger.getLogger(MasterStopTask.class.getName());

    public MasterStopTask(MasterServer ms) {
        super(ms, MasterServer.Action.Stop);
    }

    public void start() throws Exception {
        final Node node = ms.getNode();

        try {
            final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(node);

            this.future = p.getProvisioningService().stop(
                    node.toComputer().getChannel(), ms.getTaskListener(),
                    ms.getName());

            LOGGER.info("Stopping master " + ms.getName() + " on node " + node.getNodeName());

            // Set the provision started state on the master server
            ms.setStoppingState();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Stopping error for master " + ms.getName() + " on node " + node.getNodeName(), e);

            ms.setStoppingErrorState(e);
            throw e;
        }
    }

    public MasterServerTask end() throws Exception {
        final Node node = ms.getNode();

        try {
            future.get();

            LOGGER.info("Stopping completed for master " + ms.getName() + " on node " + node.getNodeName());

            // Set the provision completed state on the master server
            ms.setStoppedState();
        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Stopping completion error for master " + ms.getName() + " on node " + node.getNodeName(), cause);

            ms.setStoppingErrorState(cause);
            throw e;
        }

        return null;
    }
}
