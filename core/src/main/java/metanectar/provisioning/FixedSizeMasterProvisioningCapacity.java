package metanectar.provisioning;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Node;
import metanectar.model.MasterServer;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collection;

/**
 * A fixed sized master provisioning capacity implementation.
 *
 * @author Paul Sandoz
 */
public class FixedSizeMasterProvisioningCapacity extends MasterProvisioningCapacity {
    /**
     * The maximum number of masters that can be provisioned for this node.
     * <p>
     */
    private int maxMasters;

    /**
     * Construct with the maximum number of masters that may be provisioned.
     *
     * @param maxMasters
     */
    @DataBoundConstructor
    public FixedSizeMasterProvisioningCapacity(int maxMasters) {
        this.maxMasters = maxMasters;
    }

    /**
     *
     * @return the maximum number of masters.
     */
    public int getMaxMasters() {
        return maxMasters;
    }

    @Override
    public int getFree(Node n, Collection<MasterServer> provisioned) {
        return maxMasters - provisioned.size();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MasterProvisioningCapacity> {
        public String getDisplayName() {
            return "Fixed size master provisioning capacity";
        }
    }
}