package metanectar.provisioning;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import metanectar.Config;
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
        this.s = new CommandMasterProvisioningService(
                c.getMasterProvisioningBasePort(),
                c.getMasterProvisioningHomeLocation(),
                c.getMasterProvisioningTimeOut(),
                c.getMasterProvisioningScriptProvision(),
                c.getMasterProvisioningScriptStart(),
                c.getMasterProvisioningScriptStop(),
                c.getMasterProvisioningScriptTerminate());
    }

    @Override
    public Future<Master> provision(VirtualChannel channel, TaskListener listener, int id, String organization, URL metaNectarEndpoint, Map<String, Object> properties) throws Exception {
        return s.provision(channel, listener, id, organization, metaNectarEndpoint, properties);
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
    public Future<?> terminate(VirtualChannel channel, TaskListener listener, String organization, boolean clean) throws Exception {
        return s.terminate(channel, listener, organization, clean);
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<MasterProvisioningService> {
        public String getDisplayName() {
            return "Configured Command Provisioning Service";
        }
    }

}
