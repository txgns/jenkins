package metanectar.provisioning;

import com.google.common.collect.Multimap;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The master provisioning node property.
 *
 * @author Paul Sandoz
 */
public class MasterProvisioningNodeProperty extends NodeProperty<Node> {

    /**
     * The maximum number of masters that can be provisioned for this node.
     * <p>
     * TODO should this be an implementation detail? should there be a method on MasterProvisioningService?
     */
    private int maxMasters;

    /**
     * The master provisioning service.
     * <p>
     * Instances of this must be serializable.
     * <p>
     * TODO what happens if the configuration is modified to change the master provisioning service
     * and there are masters already provisioned?
     */
    private MasterProvisioningService provisioningService;

    @DataBoundConstructor
    public MasterProvisioningNodeProperty(int maxMasters, MasterProvisioningService provisioningService) {
        this.maxMasters = maxMasters;
        this.provisioningService = provisioningService;
    }

    public MasterProvisioningNodeProperty clone() {
        return new MasterProvisioningNodeProperty(maxMasters, provisioningService);
    }

    public int getMaxMasters() {
        return maxMasters;
    }

    public MasterProvisioningService getProvisioningService() {
        return provisioningService;
    }

    public CauseOfBlockage canTake(Queue.Task task) {
        return new CauseOfBlockage() {
            @Override
            public String getShortDescription() {
                return "This node cannot take tasks. It is responsible for provisioning masters.";
            }
        };
    }

    public Collection<MasterServer> getProvisioned() {
        Multimap<Node, MasterServer> nodesAndMasters = MasterProvisioner.getProvisionedMasters(MetaNectar.getInstance());
        Node n = getNode();
        return nodesAndMasters.get(n);
    }

    // TODO setNode is never called
    private Node getNode() {
        MetaNectar mn = MetaNectar.getInstance();
        if (this == mn.getGlobalNodeProperties().get(this.getClass()))
            return mn;

        for (Node n : MetaNectar.getInstance().getNodes()) {
            if (this == n.getNodeProperties().get(this.getClass()))
                return n;
        }

        return null;
    }

    public static MasterProvisioningNodeProperty get(Node n) {
        MasterProvisioningNodeProperty np = n.getNodeProperties().get(MasterProvisioningNodeProperty.class);
        if (np != null)
            return np;

        if (n instanceof MetaNectar)
            return ((MetaNectar)n).getGlobalNodeProperties().get(MasterProvisioningNodeProperty.class);

        return null;
    }

    @Extension
    public static class DescriptorImpl extends NodePropertyDescriptor {

        @Override
		public String getDisplayName() {
            return "Master provisioning";
		}
    }

    /**
     * Automatically add the master provisioning label
     */
    @Extension
    public static class MasterLabelFinder extends LabelFinder {
        @Override
        public Collection<LabelAtom> findLabels(Node node) {

            if (MasterProvisioningNodeProperty.get(node) != null) {
                return Collections.singleton(MetaNectar.getInstance().getLabelAtom(MasterProvisioner.MASTER_LABEL_ATOM_STRING));
            } else {
                return Collections.emptySet();
            }
        }
    }
}
