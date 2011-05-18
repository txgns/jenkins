package metanectar.cloud;

import hudson.Extension;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import metanectar.MetaNectarExtensionPoint;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Paul Sandoz
 */
public class ConfiguredComputerConnector extends SSHConnector implements MetaNectarExtensionPoint {

    @DataBoundConstructor
    public ConfiguredComputerConnector(int port, String username, String password, String privatekey, String jvmOptions) {
        super(port, username, password, privatekey, jvmOptions);
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return "Pre-configured Computer Connector";
        }
    }

}
