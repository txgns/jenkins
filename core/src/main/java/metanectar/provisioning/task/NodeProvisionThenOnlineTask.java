package metanectar.provisioning.task;

import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import metanectar.model.MetaNectar;

/**
 * @author Paul Sandoz
 */
public class NodeProvisionThenOnlineTask extends NodeProvisionTask {

    public NodeProvisionThenOnlineTask(long timeout, MetaNectar mn, Cloud c, NodeProvisioner.PlannedNode pn) {
        super(timeout, mn, c, pn);
    }

    public Task end() throws Exception {
        super.end();

        final Node n = getNode();

        return new NodeOnlineTask(getTimeout(), n);
    }
}