package metanectar.provisioning;

import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.Cloud;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProvisioner;
import metanectar.cloud.NodeTerminatingRetentionStrategy;
import metanectar.cloud.MasterProvisioningCloud;
import metanectar.test.MetaNectarTestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Emulate a simple cloud that provisions dumb slaves.
 *
 * @author Paul Sandoz
 */
class TestSlaveCloud extends Cloud implements MasterProvisioningCloud {
    private final transient MetaNectarTestCase mtc;

    private final String labelString;

    private final transient Set<LabelAtom> assignedLabels;

    private final int delay;

    public TestSlaveCloud(MetaNectarTestCase mtc, int delay) {
        this(mtc, delay, "");
    }

    public TestSlaveCloud(MetaNectarTestCase mtc, int delay, String labelString) {
        super("test-cloud");
        this.mtc = mtc;
        this.delay = delay;
        this.labelString = labelString;
        this.assignedLabels = Label.parse(labelString);
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();

        if (label != null && !canProvision(label))
            return r;

        for( ; excessWorkload>0; excessWorkload-- ) {
            r.add(new NodeProvisioner.PlannedNode(name+"-slave#"+excessWorkload,
                    Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                        public Node call() throws Exception {
                            Thread.sleep(delay);

                            DumbSlave slave = mtc.createSlave(labelString, null);
                            slave.setRetentionStrategy(new NodeTerminatingRetentionStrategy.RemoveNode());
                            return slave;
                        }
                    })
                    ,1));
        }
        return r;
    }

    @Override
    public boolean canProvision(Label label) {
        return label.matches(assignedLabels);
    }

    public Descriptor<Cloud> getDescriptor() {
        throw new UnsupportedOperationException();
    }
}
