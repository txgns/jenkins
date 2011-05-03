package metanectar.provisioning.task;

import hudson.model.Node;
import metanectar.model.MasterServer;
import metanectar.provisioning.Master;
import metanectar.provisioning.MasterProvisioningNodeProperty;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class MasterStartTask extends MasterServerTask {
    private static final Logger LOGGER = Logger.getLogger(MasterStartTask.class.getName());

    public MasterStartTask(MasterServer ms) {
        super(ms);
    }

    public void start() throws Exception {
        final Node node = ms.getNode();

        try {
            final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(node);

            this.future = p.getProvisioningService().start(
                    node.toComputer().getChannel(), ms.getTaskListener(),
                    ms.getName());

            LOGGER.info("Starting master " + ms.getName() + " on node " + node.getNodeName());

            // Set the starting state on the master server
            ms.setStartingState();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Starting error for master " + ms.getName() + " on node " + node.getNodeName(), e);

            ms.setStartingErrorState(e);
            throw e;
        }
    }

    public MasterServerTask end() throws Exception {
        final Node node = ms.getNode();

        try {
            future.get();

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
