package metanectar.provisioning;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A template for the slave master provisioning node property.
 * <p>
 * This template is used to create new instances of the slave master provisioning property, and for UI
 * configuration. It provides a level of indirection since {@link metanectar.provisioning.MasterProvisioningNodeProperty} should
 * not be configured using the UI such that that property will not be presented in any UI configuration of
 * node properties of nodes.
 *
 * @author Paul Sandoz
 */
public class MasterProvisioningNodePropertyTemplate extends AbstractDescribableImpl<MasterProvisioningNodePropertyTemplate> {

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
    public MasterProvisioningNodePropertyTemplate(MasterProvisioningCapacity capacityService, MasterProvisioningService provisioningService) {
        this.capacityService = capacityService;
        this.provisioningService = provisioningService;
    }

    public MasterProvisioningNodePropertyTemplate(int maxMasters, MasterProvisioningService provisioningService) {
        this(new FixedSizeMasterProvisioningCapacity(maxMasters), provisioningService);
    }

    public MasterProvisioningCapacity getCapacityService() {
        return capacityService;
    }

    public MasterProvisioningService getProvisioningService() {
        return provisioningService;
    }

    public MasterProvisioningNodeProperty toMasterProvisioningNodeProperty() {
        return new MasterProvisioningNodeProperty(capacityService, provisioningService);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MasterProvisioningNodePropertyTemplate> {
        public String getDisplayName() {
            return "Master provisioning node property template";
        }
    }
}
