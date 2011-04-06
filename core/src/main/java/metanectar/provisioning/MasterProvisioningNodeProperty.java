package metanectar.provisioning;

import com.google.common.collect.Sets;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.NodeProperty;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The master provisioning node property.
 *
 * @author Paul Sandoz
 */
public class MasterProvisioningNodeProperty extends NodeProperty<Node> {

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

    @DataBoundConstructor
    public MasterProvisioningNodeProperty(int maxMasters, MasterProvisioningService mps) {
        this.maxMasters = maxMasters;
        this.mps = mps;
    }

    public int getMaxMasters() {
        return maxMasters;
    }

    public MasterProvisioningService getProvisioningService() {
        return mps;
    }

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
