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
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

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
     * The master provisioning capacity service.
     */
    private MasterProvisioningCapacity capacityService;

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
    public MasterProvisioningNodeProperty(MasterProvisioningCapacity capacityService, MasterProvisioningService provisioningService) {
        this.capacityService = capacityService;
        this.provisioningService = provisioningService;
    }

    public MasterProvisioningNodeProperty(int maxMasters, MasterProvisioningService provisioningService) {
        this(new FixedSizeMasterProvisioningCapacity(maxMasters), provisioningService);
        this.capacityService = capacityService;
    }

    public MasterProvisioningNodeProperty clone() {
        return new MasterProvisioningNodeProperty(capacityService, provisioningService);
    }

    public MasterProvisioningCapacity geCapacityService() {
        return capacityService;
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
        return MetaNectar.getInstance().masterProvisioner.getProvisionedMasters().get(getNode());
    }

    // TODO setNode is never called
    private Node getNode() {
        MetaNectar mn = MetaNectar.getInstance();
        if (this == mn.getNodeProperties().get(this.getClass()))
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
            return ((MetaNectar)n).getNodeProperties().get(MasterProvisioningNodeProperty.class);

        return null;
    }

    public NodeProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return this;
    }

    @Extension
    public static class DescriptorImpl extends NodePropertyDescriptor {

        @Override
		public String getDisplayName() {
            return null;
		}
    }
}
