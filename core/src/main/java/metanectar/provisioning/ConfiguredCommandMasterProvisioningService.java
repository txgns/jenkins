package metanectar.provisioning;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import metanectar.Config;
import metanectar.property.DefaultValue;
import metanectar.property.Property;
import org.kohsuke.stapler.DataBoundConstructor;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * A configured master provisioning service that executes commands to provision and terminate masters.
 *
 * @author Paul Sandoz
 */
public class ConfiguredCommandMasterProvisioningService extends MasterProvisioningService {
    // Delegate instead of extend so information will not get serialized
    private transient CommandMasterProvisioningService s;

    public static class Properties {
        private int basePort;

        private String homeLocation;

        private int timeOut;

        private String provision;

        private String start;

        private String stop;

        private String terminate;

        @Property("metaNectar.master.provisioning.basePort") @DefaultValue("9090")
        public void setBasePort(int basePort) {
            this.basePort = basePort;
        }

        @Property("metaNectar.master.provisioning.homeLocation")
        public void setHomeLocation(String homeLocation) {
            this.homeLocation = homeLocation;
        }

        @Property("metaNectar.master.provisioning.timeOut") @DefaultValue("60")
        public void setTimeOut(int timeOut) {
            this.timeOut = timeOut;
        }

        @Property("metaNectar.master.provisioning.script.provision")
        public void setProvision(String provision) {
            this.provision = provision;
        }

        @Property("metaNectar.master.provisioning.script.start")
        public void setStart(String start) {
            this.start = start;
        }

        @Property("metaNectar.master.provisioning.script.stop")
        public void setStop(String stop) {
            this.stop = stop;
        }

        @Property("metaNectar.master.provisioning.script.terminate")
        public void setTerminate(String terminate) {
            this.terminate = terminate;
        }
    }

    @DataBoundConstructor
    public ConfiguredCommandMasterProvisioningService() {
        init();
    }

    public ConfiguredCommandMasterProvisioningService(Config c) {
        init(c);
    }

    private Object readResolve() {
        init();
        return this;
    }

    private void init() {
        init(Config.getInstance());
    }

    private void init(Config c) {
        Properties p = c.getBean(Properties.class);

        this.s = new CommandMasterProvisioningService(
                p.basePort,
                p.homeLocation,
                p.timeOut,
                p.provision,
                p.start,
                p.stop,
                p.terminate);
    }

    @Override
    public Future<Provisioned> provision(VirtualChannel channel, TaskListener listener, int id, String name, URL metaNectarEndpoint, Map<String, Object> properties) throws Exception {
        return s.provision(channel, listener, id, name, metaNectarEndpoint, properties);
    }

    @Override
    public Future<?> start(VirtualChannel channel, TaskListener listener, String name) throws Exception {
        return s.start(channel, listener, name);
    }

    @Override
    public Future<?> stop(VirtualChannel channel, TaskListener listener, String name) throws Exception {
        return s.stop(channel, listener, name);
    }

    @Override
    public Future<Terminated> terminate(VirtualChannel channel, TaskListener listener, String organization) throws Exception {
        return s.terminate(channel, listener, organization);
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<MasterProvisioningService> {
        public String getDisplayName() {
            return "Pre-configured Provisioning Service";
        }
    }

}
