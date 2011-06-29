package metanectar.provisioning.task;

import hudson.model.Node;
import metanectar.model.MasterServer;
import metanectar.provisioning.MasterProvisioningService;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author Paul Sandoz
 */
public class MasterProvisionThenStartTask extends MasterProvisionTask {

    public MasterProvisionThenStartTask(long timeout, MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties, Node node, int id) {
        super(timeout, ms, metaNectarEndpoint, properties, node, id);
    }

    public MasterServerTask end() throws Exception {
        super.end();

        return new MasterStartTask(getTimeout(), ms);
    }
}
