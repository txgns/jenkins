package metanectar.cloud;

import hudson.Extension;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import metanectar.Config;
import metanectar.MetaNectarExtensionPoint;
import metanectar.property.DefaultValue;
import metanectar.property.Property;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author Paul Sandoz
 */
public class ConfiguredComputerConnector extends SSHConnector implements MetaNectarExtensionPoint {

    public static class Properties {
        private String key;

        private String userName;

        private String userPassword;

        private int port;

        private String jvmOptions;

        @Property("metaNectar.master.ssh.key.private")
        public void setKey(String key) {
            this.key = key;
        }

        @Property("metaNectar.master.ssh.username")
        public void setUserName(String userName) {
            this.userName = userName;
        }

        @Property("metaNectar.master.ssh.password")
        public void setUserPassword(String userPassword) {
            this.userPassword = userPassword;
        }

        @Property("metaNectar.master.ssh.port") @DefaultValue("22")
        public void setPort(int port) {
            this.port = port;
        }

        @Property("metaNectar.master.ssh.jvmOptions")
        public void setJvmOptions(String jvmOptions) {
            this.jvmOptions = jvmOptions;
        }
    }

    @DataBoundConstructor
    public ConfiguredComputerConnector(int port, String username, String password, String privatekey, String jvmOptions) {
        super(port, username, password, privatekey, jvmOptions);
    }

    public ConfiguredComputerConnector() {
        this(Config.getInstance().getBean(Properties.class));
    }

    private ConfiguredComputerConnector(Properties p) {
        super(p.port, p.userName, p.userPassword, p.key, p.jvmOptions);
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return "Pre-configured Computer Connector";
        }
    }

}
