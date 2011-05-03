package metanectar.provisioning.task;

import hudson.model.Node;
import metanectar.model.MasterServer;

import java.net.URL;
import java.util.Map;

/**
 * @author Paul Sandoz
 */
public class MasterProvisionThenStartTask extends MasterProvisionTask {

    public MasterProvisionThenStartTask(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties, Node node, int id) {
        super(ms, metaNectarEndpoint, properties, node, id);
    }

    public MasterServerTask end() throws Exception {
        super.end();

        return new MasterStartTask(ms);
    }
}
