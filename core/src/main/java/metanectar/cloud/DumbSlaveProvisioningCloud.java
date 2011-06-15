package metanectar.cloud;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.RetentionStrategy;
import hudson.util.DescribableList;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A cloud that provisions dumb slaves.
 *
 * @author Stephen Connolly
 */
public class DumbSlaveProvisioningCloud extends AbstractCloudImpl {

    private  String nodeDescription;

    private  String remoteFS;

    private  String numExecutors;

    private  Node.Mode mode;

    private  String labelString;

    private  ComputerLauncher launcher;

    private  RetentionStrategy retentionStrategy;

    private DescribableList<NodeProperty<?>,NodePropertyDescriptor> nodeProperties = new DescribableList<NodeProperty<?>,NodePropertyDescriptor>(Hudson.getInstance());

    private static final Logger LOGGER = Logger.getLogger(DumbSlaveProvisioningCloud.class.getName());

    private transient Set<Node> provisionedNodes = Collections.synchronizedSet(new HashSet<Node>());

    @DataBoundConstructor
    public DumbSlaveProvisioningCloud(String name, String instanceCapStr, String nodeDescription, String remoteFS,
                                      String numExecutors,
                                      Node.Mode mode, String labelString, ComputerLauncher launcher,
                                      RetentionStrategy retentionStrategy,
                                      List<? extends NodeProperty<?>> nodeProperties) throws IOException {
        super(name, instanceCapStr);

        this.nodeDescription = nodeDescription;
        this.remoteFS = remoteFS;
        this.numExecutors = numExecutors;
        this.mode = mode;
        this.labelString = labelString;
        this.launcher = launcher;
        this.retentionStrategy = new NodeTerminatingRetentionStrategy.RemoveNode();
        this.nodeProperties.replaceBy(nodeProperties);
    }

    private Object readResolve() {
        if (provisionedNodes == null) provisionedNodes = new HashSet<Node>();
        return this;
    }

    public Slave toSlave(String name) throws Descriptor.FormException, IOException {
        return new SlaveImpl(name, nodeDescription, remoteFS, numExecutors, mode, labelString,
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

    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
    	return nodeProperties;
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();

        for( ; excessWorkload>0; excessWorkload-- ) {
            final String slaveName = name + "-" + UUID.randomUUID();
            r.add(new NodeProvisioner.PlannedNode(slaveName,
                    Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                        public Node call() throws Exception {
                            LOGGER.log(Level.INFO, "launching slave {0}", slaveName);
                            Slave slave = toSlave(slaveName);
                            provisionedNodes.add(slave);
                            return slave;
                       }
                    })
                    ,1));
        }
        return r;
    }

    @Override
    public boolean canProvision(Label label) {
        LOGGER.log(Level.INFO, "canProvision({0}) on {1}", new Object[]{label, labelString});
        return (label == null ? Node.Mode.NORMAL.equals(mode) : label.matches(Label.parse(labelString)))
                && provisionedNodes.size() < getInstanceCap();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        public String getDisplayName() {
            return Messages.DumbSlaveProvisioningCloud_DisplayName();
        }

        public static List<NodePropertyDescriptor> getNodePropertyDescriptors() {
            List<NodePropertyDescriptor> result = new ArrayList<NodePropertyDescriptor>();
            Collection<NodePropertyDescriptor> list = (Collection) Hudson.getInstance().getDescriptorList(NodeProperty.class);
            for (NodePropertyDescriptor npd : list) {
                if (npd.isApplicable(SlaveImpl.class)) {
                    result.add(npd);
                }
            }
            return result;
        }


    }

    private static class ComputerImpl extends AbstractCloudComputer<SlaveImpl> {

        public boolean isDirty;

        public ComputerImpl(SlaveImpl slave) {
            super(slave);
        }
    }

    private class SlaveImpl extends AbstractCloudSlave {
        public SlaveImpl(String name, String nodeDescription, String remoteFS, String numExecutors, Mode mode,
                         String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy,
                         List<? extends NodeProperty<?>> nodeProperties) throws Descriptor.FormException, IOException {
            super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy,
                    nodeProperties);    //To change body of overridden methods use File | Settings | File Templates.
        }

        public SlaveImpl(String name, String nodeDescription, String remoteFS, int numExecutors, Mode mode,
                         String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy,
                         List<? extends NodeProperty<?>> nodeProperties) throws Descriptor.FormException, IOException {
            super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy,
                    nodeProperties);    //To change body of overridden methods use File | Settings | File Templates.
        }

        @Override
        public ComputerImpl createComputer() {
            return new ComputerImpl(this);
        }

        @Override
        protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
            if (provisionedNodes.remove(this)) {
                LOGGER.log(Level.INFO, "terminating slave {0}", name);
                listener.getLogger().println(getNodeName() + " terminated.");
            } else {
                listener.getLogger().println(getNodeName() + " was already terminated.");
            }
        }

        @Override
        public int hashCode() {
            return super.hashCode();    //To change body of overridden methods use File | Settings | File Templates.
        }
    }

}