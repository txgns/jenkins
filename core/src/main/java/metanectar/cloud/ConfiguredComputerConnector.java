package metanectar.cloud;

import hudson.Extension;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import metanectar.Config;
import metanectar.MetaNectarExtensionPoint;
import metanectar.model.MetaNectar;
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
        super(p.getPort(), p.getUserName(), p.getUserPassword(), p.getPrivateKey(), p.getJvmOptions());
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return "DEV@cloud Private Edition computer connector";
        }
    }

}
