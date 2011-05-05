package metanectar.provisioning.task;

import hudson.model.Node;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;
import metanectar.provisioning.Master;
import metanectar.provisioning.MasterProvisioningNodeProperty;
import metanectar.provisioning.MasterProvisioningService;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class MasterProvisionTask extends MasterServerTask<Master> {
    private static final Logger LOGGER = Logger.getLogger(MasterProvisionTask.class.getName());

    private final URL metaNectarEndpoint;

    private final Map<String, Object> properties;

    private final Node node;

    private final int id;

    public MasterProvisionTask(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties, Node node, int id) {
        super(ms, MasterServer.Action.Provision);

        this.metaNectarEndpoint = metaNectarEndpoint;
        this.properties = properties;
        this.node = node;
        this.id = id;
    }

    public void start() throws Exception {
        try {
            final MasterProvisioningNodeProperty p = MasterProvisioningNodeProperty.get(node);

            Map<String, Object> provisionProperties = new HashMap<String, Object>(properties);
            provisionProperties.put(MasterProvisioningService.PROPERTY_PROVISION_GRANT_ID, ms.getGrantId());

            this.future = p.getProvisioningService().provision(
                    node.toComputer().getChannel(), ms.getTaskListener(),
                    id, ms.getName(), metaNectarEndpoint, provisionProperties);

            LOGGER.info("Provisioning master " + ms.getName() + " on node " + node.getNodeName());

            // Set the provision started state on the master server
            ms.setProvisionStartedState(node, id);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Provisioning error for master " + ms.getName() + " on node " + node.getNodeName(), e);

            ms.setProvisionErrorState(node, e);
            throw e;
        }
    }

    public MasterServerTask end() throws Exception {
        try {
            final Master m = future.get();

            LOGGER.info("Provisioning completed for master " + ms.getName() + " on node " + node.getNodeName());

            // Set the provision completed state on the master server
            ms.setProvisionCompletedState(node, m.endpoint);
        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Provisioning completion error for master " + ms.getName() + " on node " + node.getNodeName(), cause);

            ms.setProvisionErrorState(node, cause);
            throw e;
        }

        return null;
    }
}
