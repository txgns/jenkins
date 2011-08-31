package metanectar.provisioning.task;

import hudson.model.Node;
import metanectar.model.MasterServer;
import metanectar.provisioning.MasterProvisioningNodeProperty;
import metanectar.provisioning.MasterProvisioningService;

import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class MasterTerminateTask extends MasterServerTask<MasterProvisioningService.Terminated> {
    private static final Logger LOGGER = Logger.getLogger(MasterTerminateTask.class.getName());

    private final boolean force;

    public MasterTerminateTask(long timeout, MasterServer ms, boolean force) {
        super(timeout, ms, MasterServer.Action.Terminate);
        this.force = force;
    }

    public Future<MasterProvisioningService.Terminated> doStart() throws Exception {
        final Node node = ms.getNode();

        try {
            LOGGER.info("Terminating master " + ms.getName() + " on node " + node.getNodeName());

            // Set terminate stated state on the master server
            ms.setTerminateStartedState();

            final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(node);

            return p.getProvisioningService().terminate(ms);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Terminating error for master " + ms.getName() + " on node " + node.getNodeName(), e);

            ms.setTerminateErrorState(e);
            if (force) {
                ms.setTerminateCompletedState(null);
            }
            throw e;
        }
    }

    public MasterServerTask end(Future<MasterProvisioningService.Terminated> f) throws Exception {
        final Node node = ms.getNode();

        try {
            final MasterProvisioningService.Terminated terminated = f.get();

            LOGGER.info("Terminating completed for master " + ms.getName() + " on node " + node.getNodeName());

            // Set terminate completed state on the master server
            ms.setTerminateCompletedState(terminated.getHomeSnapshot());
        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Terminating completion error for master " + ms.getName() + " on node " + node.getNodeName(), cause);

            ms.setTerminateErrorState(cause);
            if (force) {
                ms.setTerminateCompletedState(null);
            }
            throw e;
        }

        return null;
    }
}
