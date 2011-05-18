package metanectar.cloud;

import hudson.Extension;
import hudson.model.*;
import hudson.slaves.*;
import hudson.util.DescribableList;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * A master provisioning dumb slave cloud.
 *
 * @author Paul Sandoz
 */
public class MasterProvisioningDumbSlaveCloud extends Cloud implements MasterProvisioningCloud {
    private final int delay;

    private  String nodeDescription;

    private  String remoteFS;

    private  String numExecutors;

    private  Node.Mode mode;

    private  String labelString;

    private  ComputerLauncher launcher;

    private  RetentionStrategy retentionStrategy;

    private DescribableList<NodeProperty<?>,NodePropertyDescriptor> nodeProperties = new DescribableList<NodeProperty<?>,NodePropertyDescriptor>(Hudson.getInstance());

    @DataBoundConstructor
    public MasterProvisioningDumbSlaveCloud(String nodeDescription, String remoteFS, String numExecutors,
                             Node.Mode mode, String labelString, ComputerLauncher launcher,
                             RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws IOException {
        super("master-provisioning-dumb-slave-cloud");

        this.nodeDescription = nodeDescription;
        this.remoteFS = remoteFS;
        this.numExecutors = numExecutors;
        this.mode = mode;
        this.labelString = labelString;
        this.launcher = launcher;
        this.retentionStrategy = new NodeTerminatingRetentionStrategy.RemoveNode();
        this.nodeProperties.replaceBy(nodeProperties);

        this.delay = 1000;
    }

    public DumbSlave toDumbSlave(String name) throws Descriptor.FormException, IOException {
        return new DumbSlave(name, nodeDescription, remoteFS, numExecutors, mode, labelString,
                launcher, retentionStrategy, nodeProperties.toList());
    }

    public String getNodeDescription() {
        return nodeDescription;
    }

    public String getRemoteFS() {
        return remoteFS;
    }

    public String getNumExecutors() {
        return numExecutors;
    }

    public Node.Mode getMode() {
        return mode;
    }

    public String getLabelString() {
        return labelString;
    }

    public ComputerLauncher getLauncher() {
        return launcher;
    }

    public RetentionStrategy getRetentionStrategy() {
        return retentionStrategy;
    }

    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
    	return nodeProperties;
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();

        for( ; excessWorkload>0; excessWorkload-- ) {
            final String slaveName = "dumb-slave-cloud-" + UUID.randomUUID();
            r.add(new NodeProvisioner.PlannedNode(slaveName,
                    Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                        public Node call() throws Exception {
                            Thread.sleep(delay);

                            System.out.println("launching slave");

                            DumbSlave s = toDumbSlave(slaveName);

                            Hudson.getInstance().addNode(s);
                            s.toComputer().connect(false).get();
                            return s;
                       }
                    })
                    ,1));
        }
        return r;
    }

    @Override
    public boolean canProvision(Label label) {
        return false;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        public String getDisplayName() {
            return "Master Provisioning Dumb Cloud";
        }

        public Class<DumbSlave> getDumbSlaveClass() {
            return DumbSlave.class;
        }

        public Slave.SlaveDescriptor getDumbSlaveDescriptor() {
            Descriptor d = Hudson.getInstance().getDescriptorOrDie(DumbSlave.class);
            if (d instanceof Slave.SlaveDescriptor)
                return (Slave.SlaveDescriptor) d;
            throw new IllegalStateException(d.getClass()+" needs to extend from SlaveDescriptor");
        }

    }
}