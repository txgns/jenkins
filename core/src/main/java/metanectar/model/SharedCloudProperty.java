package metanectar.model;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ReconfigurableDescribable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collection;
import java.util.List;

/**
 * @author Stephen Connolly
 */
public abstract class SharedCloudProperty<S extends SharedCloud>
        implements ReconfigurableDescribable<SharedCloudProperty<?>>, ExtensionPoint {
    protected transient S owner;

    public void setOwner(S owner) {
        this.owner = owner;
    }

    public Descriptor<SharedCloudProperty<?>> getDescriptor() {
        return (SharedCloudPropertyDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public SharedCloudProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return form==null ? null : getDescriptor().newInstance(req, form);
    }

    public abstract Collection<? extends Action> getCloudActions(SharedCloud m);

    /**
     * Lists up all the registered {@link SharedCloudPropertyDescriptor}s in the system.
     */
    public static DescriptorExtensionList<SharedCloudProperty<?>,SharedCloudPropertyDescriptor> all() {
        return (DescriptorExtensionList) Hudson.getInstance().getDescriptorList(SharedCloudProperty.class);
    }

    /**
     * List up all {@link SharedCloudPropertyDescriptor}s that are applicable for the
     * given connected master.
     */
    public static List<SharedCloudPropertyDescriptor> for_(SharedCloud node) {
        return SharedCloudPropertyDescriptor.for_(all(), node);
    }

}
