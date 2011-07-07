package metanectar.provisioning.task;

import hudson.model.Node;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class NodeOnlineTask extends TaskWithFuture {
    private static final Logger LOGGER = Logger.getLogger(NodeOnlineTask.class.getName());

    private final Node node;

    public NodeOnlineTask(long timeout, Node n) {
        super(timeout);
        this.node = n;
    }

    public void start() throws Exception {
        try {
            LOGGER.info("Connecting to node " + node.getNodeName());

            setFuture((Future)node.toComputer().connect(false));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Connecting error to node " + node.getNodeName(), e);

            throw e;
        }
    }

    public Task end() throws Exception {
        try {
            getFuture().get();

            LOGGER.info("Connecting completed to node " + node.getNodeName());
        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Connecting completion error to node " + node.getNodeName(), cause);

            throw e;
        }

        return null;
    }
}
