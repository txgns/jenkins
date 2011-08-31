package metanectar.provisioning.task;

import hudson.model.Node;
import metanectar.model.MasterServer;
import metanectar.provisioning.MasterProvisioningNodeProperty;
import metanectar.provisioning.MasterProvisioningService;
import metanectar.provisioning.MasterProvisioningService.Provisioned;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class MasterStartTask extends MasterServerTask {
    private static final Logger LOGGER = Logger.getLogger(MasterStartTask.class.getName());

    public MasterStartTask(long timeout, MasterServer ms) {
        super(timeout, ms, MasterServer.Action.Start);
    }

    public Future doStart() throws Exception {
        final Node node = ms.getNode();

        try {
            LOGGER.info("Starting master " + ms.getName() + " on node " + node.getNodeName());

            // Set the starting state on the master server
            ms.setStartingState();

            final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(node);

            return p.getProvisioningService().start(ms);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Starting error for master " + ms.getName() + " on node " + node.getNodeName(), e);

            ms.setStartingErrorState(e);
            throw e;
        }
    }

    public MasterServerTask end(Future f) throws Exception {
        final Node node = ms.getNode();

        try {
            f.get();

            LOGGER.info("Starting completed for master " + ms.getName() + " on node " + node.getNodeName());

            // Set the started state on the master server
            ms.setStartedState();
        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Starting completion error for master " + ms.getName() + " on node " + node.getNodeName(), cause);

            ms.setStartingErrorState(cause);
            throw e;
        }

        return null;
    }
}
