package metanectar.provisioning.task;

import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import metanectar.cloud.MasterProvisioningCloudListener;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class NodeProvisionThenOnlineTask extends NodeProvisionTask {

    public NodeProvisionThenOnlineTask(MetaNectar mn, Cloud c, NodeProvisioner.PlannedNode pn) {
        super(mn, c, pn);
    }

    public Task end() throws Exception {
        super.end();

        final Node n = getNode();

        return new NodeOnlineTask(n);
    }
}