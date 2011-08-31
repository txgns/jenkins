package metanectar.provisioning.task;

import hudson.model.Node;
import metanectar.model.MasterServer;
import metanectar.provisioning.MasterProvisioningNodeProperty;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class MasterStopTask extends MasterServerTask {
    private static final Logger LOGGER = Logger.getLogger(MasterStopTask.class.getName());

    public MasterStopTask(long timeout, MasterServer ms) {
        super(timeout, ms, MasterServer.Action.Stop);
    }

    public Future doStart() throws Exception {
        final Node node = ms.getNode();

        try {
            LOGGER.info("Stopping master " + ms.getName() + " on node " + node.getNodeName());

            // Set the stopping state on the master server
            ms.setStoppingState();

            final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(node);

            return p.getProvisioningService().stop(ms);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Stopping error for master " + ms.getName() + " on node " + node.getNodeName(), e);

            ms.setStoppingErrorState(e);
            throw e;
        }
    }

    public MasterServerTask end(Future f) throws Exception {
        final Node node = ms.getNode();

        try {
            f.get();

            LOGGER.info("Stopping completed for master " + ms.getName() + " on node " + node.getNodeName());

            // Set the stopped state on the master server
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
