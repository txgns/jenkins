package metanectar.provisioning.task;

import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import metanectar.model.MetaNectar;

import java.util.concurrent.Future;

/**
 * @author Paul Sandoz
 */
public class NodeProvisionThenOnlineTask extends NodeProvisionTask {

    public NodeProvisionThenOnlineTask(long timeout, MetaNectar mn, Cloud c, NodeProvisioner.PlannedNode pn) {
        super(timeout, mn, c, pn);
    }

    public Task end(Future<Node> f) throws Exception {
        super.end(f);

        final Node n = getNode();

        return new NodeOnlineTask(getTimeout(), n);
    }
}