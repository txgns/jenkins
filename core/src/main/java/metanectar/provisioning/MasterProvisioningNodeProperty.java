package metanectar.provisioning;

import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.NodeProperty;

import java.util.HashSet;
import java.util.Set;

/**
 * The master provisioning node property.
 *
 * @author Paul Sandoz
 */
public class MasterProvisioningNodeProperty extends NodeProperty<Node> {

    /**
     * The provisioned masters associated with this node.
     * <p>
     * This is independent of whether provisioned masters are online or not.
     * <p>
     * When a master is provisioned or terminated for this node then this list will be updated.
     * <p>
     * This state is not configurable and must be retained over one or more saves of configuration.
     */
    private Set<Master> provisioned;

    /**
     * The maximum number of masters that can be provisioned for this node.
     * <p>
     * TODO should this be an implementation detail? should there be a method on MasterProvisioningService?
     */
    private int maxMasters;

    /**
     * The master provisioning service.
     * <p>
     * Instances of this must be serializable.
     * <p>
     * TODO what happens if the configuration is modified to change the master provisioning service
     * and there are masters already provisioned?
     */
    private MasterProvisioningService mps;

    public MasterProvisioningNodeProperty(int maxMasters, MasterProvisioningService mps) {
        this.provisioned = new HashSet<Master>();
        this.maxMasters = maxMasters;
        this.mps = mps;
    }

    public int getMaxMasters() {
        return maxMasters;
    }

    public MasterProvisioningService getProvisioningService() {
        return mps;
    }

    public Set<Master> getProvisioned() {
        return provisioned;
    }

    public boolean isProvisioned(Master m) {
        return provisioned.contains(m);
    }

    public void provision(Master m) {
        provisioned.add(m);
    }

    public void terminate(Master m) {
        provisioned.remove(m);
    }

    // TODO use the NodeProperty reconfigure(StaplerRequest request, JSONObject form) { return this; }

    public CauseOfBlockage canTake(Queue.Task task) {
        return new CauseOfBlockage() {
            @Override
            public String getShortDescription() {
                return "This node cannot take tasks. It is responsible for provisioning masters.";
            }
        };
    }

    public static MasterProvisioningNodeProperty get(Node n) {
        return n.getNodeProperties().get(MasterProvisioningNodeProperty.class);
    }

}
