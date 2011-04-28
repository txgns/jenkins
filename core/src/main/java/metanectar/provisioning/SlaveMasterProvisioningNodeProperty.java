package metanectar.provisioning;

import com.google.common.collect.Multimap;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.LabelFinder;
import hudson.model.Node;
import hudson.model.Queue;
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

/**
 * The slave master provisioning node property.
 * <p>
 * This property will be explicitly added when slaves are configured for master provisioning. For example,
 * when a new slave is provisioned from a master provisioning cloud.
 * <p>
 * This property will not be configured using the UI.
 *
 * @author Paul Sandoz
 */
public class SlaveMasterProvisioningNodeProperty extends MasterProvisioningNodeProperty {

    @DataBoundConstructor
    public SlaveMasterProvisioningNodeProperty(int maxMasters, MasterProvisioningService provisioningService) {
        super(maxMasters, provisioningService);
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
