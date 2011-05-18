package metanectar.cloud;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.slaves.RetentionStrategy;
import metanectar.model.MetaNectar;

import java.util.logging.Logger;

/**
 * A retention strategy that terminates a node if there are no masters provisioned on that node
 * and there are no pending masters to be provisioned.
 * <p>
 * A master provisioning cloud MUST implement this strategy and set on provisioned nodes if termination of
 * nodes is to occur.
 *
 * @author Paul Sandoz
 */
public abstract class NodeTerminatingRetentionStrategy<N extends Node, C extends Computer> extends RetentionStrategy<C> {

    private static final Logger LOGGER = Logger.getLogger(NodeTerminatingRetentionStrategy.class.getName());

    @Override
    public void start(C c) {
        c.connect(false);
    }

    public long check(C c) {
        return 60;
    }

    /**
     * Terminate the node;
     *
     * @param n the node to terminate.
     *
     * @throws Exception if an exception occurs when terminating.
     */
    public abstract void terminate(N n) throws Exception;

    public static class RemoveNode extends NodeTerminatingRetentionStrategy {
        @Override
        public void terminate(Node node) throws Exception {
            MetaNectar.getInstance().removeNode(node);
        }
    }
}
