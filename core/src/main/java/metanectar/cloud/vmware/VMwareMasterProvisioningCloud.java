package metanectar.cloud.vmware;

import com.google.common.util.concurrent.ForwardingFuture;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.Node;
import hudson.plugins.vmware.cloud.CloudImpl;
import hudson.plugins.vmware.cloud.VMWareComputer;
import hudson.plugins.vmware.cloud.VMWareSlave;
import hudson.slaves.ComputerConnector;
import hudson.slaves.NodeProvisioner;
import metanectar.cloud.CloudTerminatingRetentionStrategy;
import metanectar.cloud.MasterProvisioningCloud;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Paul Sandoz
 */
public class VMwareMasterProvisioningCloud extends CloudImpl implements MasterProvisioningCloud {

    @DataBoundConstructor
    public VMwareMasterProvisioningCloud(String poolName, String labelString, String remoteFS, ComputerConnector connector) {
        super(poolName, labelString, false, remoteFS, 1, 5, connector);
    }

    @Extension
    public static class DescriptorImpl extends CloudImpl.DescriptorImpl {

        @Override
        public String getDisplayName() {
            return "Pooled VMWare Virtual Machines";
        }
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        final Collection<NodeProvisioner.PlannedNode> delegatedNodes = super.provision(label, excessWorkload);
        final Collection<NodeProvisioner.PlannedNode> pns = new ArrayList<NodeProvisioner.PlannedNode>(delegatedNodes.size());

        for (final NodeProvisioner.PlannedNode delegated : delegatedNodes) {
            pns.add(new NodeProvisioner.PlannedNode(delegated.displayName, adapt(delegated.future), delegated.numExecutors));
        }

        return pns;
    }

    // TODO change VMware cloud impl so as this is not necessary
    private Future<Node> adapt(final Future<Node> fn) {
        return new ForwardingFuture<Node>() {
            @Override
            protected Future<Node> delegate() {
                return fn;
            }

            @Override
            public Node get() throws InterruptedException, ExecutionException {
                return process(super.get());
            }

            @Override
            public Node get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return process(super.get(timeout, unit));
            }

            private Node process(Node n) throws ExecutionException {
                final VMWareSlave s = (VMWareSlave)n;
                s.setRetentionStrategy(new CloudTerminatingRetentionStrategy<VMWareSlave, VMWareComputer>() {
                    @Override
                    public void terminate(VMWareSlave vmWareSlave) throws Exception {
                        s.terminate();
                    }
                });
                return n;
            }
        };
    }

}
