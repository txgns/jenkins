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
public abstract class SharedSlaveProperty<S extends SharedSlave>
        implements ReconfigurableDescribable<SharedSlaveProperty<?>>, ExtensionPoint {
    protected transient S owner;

    public void setOwner(S owner) {
        this.owner = owner;
    }

    public Descriptor<SharedSlaveProperty<?>> getDescriptor() {
        return (SharedSlavePropertyDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public SharedSlaveProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return form==null ? null : getDescriptor().newInstance(req, form);
    }

    public abstract Collection<? extends Action> getSlaveActions(SharedSlave m);

    /**
     * Lists up all the registered {@link metanectar.model.SharedSlavePropertyDescriptor}s in the system.
     */
    public static DescriptorExtensionList<SharedSlaveProperty<?>,SharedSlavePropertyDescriptor> all() {
        return (DescriptorExtensionList) Hudson.getInstance().getDescriptorList(SharedSlaveProperty.class);
    }

    /**
     * List up all {@link metanectar.model.SharedSlavePropertyDescriptor}s that are applicable for the
     * given connected master.
     */
    public static List<SharedSlavePropertyDescriptor> for_(SharedSlave node) {
        return SharedSlavePropertyDescriptor.for_(all(), node);
    }

}
