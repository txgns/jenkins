package metanectar.provisioning.task;

import hudson.model.Node;
import hudson.util.Futures;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class NodeOnlineTask extends TaskWithTimeout<Void,NodeOnlineTask> {
    private static final Logger LOGGER = Logger.getLogger(NodeOnlineTask.class.getName());

    private final Node node;

    public NodeOnlineTask(long timeout, Node n) {
        super(timeout);
        this.node = n;
    }

    public Future<Void> doStart() throws Exception {
        try {
            LOGGER.info("Connecting to node " + node.getNodeName());

            return Futures.adaptToVoid(node.toComputer().connect(false));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Connecting error to node " + node.getNodeName(), e);

            throw e;
        }
    }

    public NodeOnlineTask end(Future<Void> execution) throws Exception {
        try {
            execution.get();

            LOGGER.info("Connecting completed to node " + node.getNodeName());
        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Connecting completion error to node " + node.getNodeName(), cause);

            throw e;
        }

        return null;
    }
}
