package metanectar.provisioning.slave;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import metanectar.MetaNectarExtensionPoint;
import metanectar.model.MetaNectar;
import metanectar.provisioning.MasterProvisioningNodePropertyTemplate;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.List;

/**
 * A master provisioned node, explicitly configured, for computer-based master provisioning.
 * <p>
 * @author Paul Sandoz
 */
public class MasterProvisioningSlave extends Slave implements MetaNectarExtensionPoint {

    private MasterProvisioningNodePropertyTemplate template;

    @DataBoundConstructor
    public MasterProvisioningSlave(String name,
                                   MasterProvisioningNodePropertyTemplate template,
                                   String nodeDescription,
                                   String labelString,
                                   ComputerLauncher launcher,
                                   List<? extends NodeProperty<?>> nodeProperties) throws IOException, Descriptor.FormException {
        super(
                name,
                nodeDescription,
                MetaNectar.getInstance().getConfig().getRemoteFS(),
                1,
                Mode.NORMAL,
                labelString,
                launcher,
                RetentionStrategy.INSTANCE,
                nodeProperties);

        getNodeProperties().add(template.toMasterProvisioningNodeProperty());
    }

    public MasterProvisioningNodePropertyTemplate getTemplate() {
        return template;
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {
        public String getDisplayName() {
            return "Master Provisioning Node";
        }

        public List<Descriptor<ComputerLauncher>> getComputerLauncherDescriptors() {
            return MetaNectar.allWithMetaNectarExtensions(ComputerLauncher.class);
        }
    }
}