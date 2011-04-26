package metanectar.provisioning;

import com.google.common.collect.Maps;
import hudson.*;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.LocalChannel;
import hudson.remoting.VirtualChannel;
import metanectar.Config;
import metanectar.model.MetaNectar;
import metanectar.provisioning.HomeDirectoryProvisioner;
import metanectar.provisioning.Master;
import metanectar.provisioning.MasterProvisioningService;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    private Object readResolve() {
        init();
        return this;
    }

    private void init() {
        this.s = new CommandMasterProvisioningService(
                Config.getInstance().getMasterProvisioningBasePort(),
                Config.getInstance().getMasterProvisioningHomeLocation(),
                Config.getInstance().getMasterProvisioningTimeOut(),
                Config.getInstance().getMasterProvisioningScriptProvision(),
                Config.getInstance().getMasterProvisioningScriptTerminate());
    }

    @Override
    public Future<Master> provision(VirtualChannel channel, TaskListener listener, int id, String organization, URL metaNectarEndpoint, Map<String, Object> properties) throws Exception {
        return s.provision(channel, listener, id, organization, metaNectarEndpoint, properties);
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
