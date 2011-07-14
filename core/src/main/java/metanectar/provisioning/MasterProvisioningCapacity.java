package metanectar.provisioning;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Node;
import metanectar.model.MasterServer;

import java.util.Collection;

/**
 * An extension point to provide estimates of master provisioning capacity.
 *
 * <p>The master provisioning algorithm will use a selected implementation in conjunction with a selected
 * implementation of {@link MasterProvisioningService} to determine whether a master can be provisioned
 * on a {@link Node}</p>
 *
 * <p>TODO type this according to the MasterProvisioningService ?</p>
 *
 * @author Paul Sandoz
 * @see MasterProvisioningService
 * @see MasterProvisioningNodeProperty
 */
public abstract class MasterProvisioningCapacity extends AbstractDescribableImpl<MasterProvisioningCapacity> implements ExtensionPoint {

    /**
     * Get the estimated number of masters that may be provisioned on the {@link Node} <code>n</code>.
     *
     * <p>Depending on the implementation the returned value may be different when this method is invoked at
     * different times.</p>
     *
     * <p>An implementation of this method should return as quickly as possible. If CPU, disk and/or memory is
     * monitored then consider using a separate and local concurrent task that gathers data from nodes from which that
     * implementation can query that data.</p>
     *
     * @param n the {@link Node} for which masters are and may be provisioned on.
     * @param provisioned the current set of provisioned masters.
     * @return the estimated number of masters that can be provisioned. The sum of this value and the size of the
     *         current set of <code>provisioned</code> masters yields the estimated total sum of masters that can be
     *         provisioned on the node. If this value is less than zero then the current node is over-provisioned.
     */
    public abstract int getFree(Node n, Collection<MasterServer> provisioned);

    /**
     * Returns all the registered {@link MasterProvisioningCapacity} descriptors.
     */
    public static DescriptorExtensionList<MasterProvisioningCapacity, Descriptor<MasterProvisioningCapacity>> all() {
        return Hudson.getInstance().<MasterProvisioningCapacity, Descriptor<MasterProvisioningCapacity>>getDescriptorList(MasterProvisioningCapacity.class);
    }

}
