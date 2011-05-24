package metanectar.cloud;

import hudson.model.Descriptor;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerConnector;
import metanectar.MetaNectarExtensionPoint;
import metanectar.model.MetaNectar;

import java.util.ArrayList;
import java.util.List;

/**
 * The base class for cloud-based provisioning of masters or slaves.
 *
 * @author Paul Sandoz
 */
public abstract class AbstractProvisioningCloud extends Cloud implements MetaNectarExtensionPoint {

    protected AbstractProvisioningCloud(String name) {
        super(name);
    }

    /**
     * @return all the cloud-based descriptors that are assignable to AbstractProvisioningCloud.
     */
    public static List<Descriptor<Cloud>> getProvisioningDescriptors() {
        List<Descriptor<Cloud>> r = new ArrayList<Descriptor<Cloud>>();
        for (Descriptor<Cloud> d : Cloud.all()) {
            if (AbstractProvisioningCloud.class.isAssignableFrom(d.clazz)) {
                r.add(d);
            }
        }
        return r;
    }

    /**
     * @return all the cloud-based descriptors that are assignable to MasterProvisioningCloud, and are
     * thus associated with master provisioning.
     */
    public static List<Descriptor<Cloud>> getMasterProvisioningCloudDescriptors() {
        List<Descriptor<Cloud>> r = new ArrayList<Descriptor<Cloud>>();
        for (Descriptor<Cloud> d : Cloud.all()) {
            if (MasterProvisioningCloud.class.isAssignableFrom(d.clazz)) {
                r.add(d);
            }
        }
        return r;
    }

    /**
     * @return all the cloud-based descriptors that are not assignable to MasterProvisioningCloud,
     * and are thus associated with slave provisioning.
     */
    public static List<Descriptor<Cloud>> getSlaveProvisioningCloudDescriptors() {
        List<Descriptor<Cloud>> r = new ArrayList<Descriptor<Cloud>>();
        for (Descriptor<Cloud> d : Cloud.all()) {
            if (!MasterProvisioningCloud.class.isAssignableFrom(d.clazz)) {
                r.add(d);
            }
        }
        return r;
    }

    public static List<Descriptor<ComputerConnector>> getMasterProvisioningComputerConnectors() {
        return MetaNectar.allWithMetaNectarExtensions(ComputerConnector.class);
    }
}