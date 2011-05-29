package metanectar.cloud;

import hudson.Extension;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import metanectar.Config;
import metanectar.MetaNectarExtensionPoint;
import metanectar.model.MetaNectar;
import metanectar.property.DefaultValue;
import metanectar.property.Optional;
import metanectar.property.Property;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Paul Sandoz
 */
public class ConfiguredComputerConnector extends SSHConnector implements MetaNectarExtensionPoint {

    @DataBoundConstructor
    public ConfiguredComputerConnector() {
        this(MetaNectar.getInstance().getConfig().getBean(Config.SSHConnectionProperties.class));
    }

    private ConfiguredComputerConnector(Config.SSHConnectionProperties p) {
        super(p.getPort(), p.getUserName(), p.getUserPassword(), p.getKey(), p.getJvmOptions());
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return "Pre-configured Computer Connector";
        }
    }

}
