package metanectar.provisioning.slave;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import metanectar.Config;
import metanectar.MetaNectarExtensionPoint;
import metanectar.model.MetaNectar;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Paul Sandoz
 */
public class ConfiguredComputerLauncher extends SSHLauncher implements MetaNectarExtensionPoint {

    @DataBoundConstructor
    public ConfiguredComputerLauncher(String host) {
        this(host, MetaNectar.getInstance().getConfig().getBean(Config.SSHConnectionProperties.class));
    }

    private ConfiguredComputerLauncher(String host, Config.SSHConnectionProperties p) {
        super(host, p.getPort(), p.getUserName(), p.getUserPassword(), p.getPrivateKey(), p.getJvmOptions());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        @Override
        public String getDisplayName() {
            return "Pre-configured Computer Launcher";
        }
    }

}
