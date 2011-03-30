package metanectar.provisioning;

import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author Paul Sandoz
 */
public interface MasterProvisioningService {
    // TODO listener or task listener
    Future<MasterProvisioner.Master> provision(VirtualChannel channel, String organization, URL metaNectarEndpoint, String key) throws IOException, InterruptedException;

    Map<String, MasterProvisioner.Master> getProvisioned(VirtualChannel channel);
}