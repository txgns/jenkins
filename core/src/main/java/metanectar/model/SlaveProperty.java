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
public abstract class SlaveProperty<S extends Slave> implements ReconfigurableDescribable<SlaveProperty<?>>, ExtensionPoint {
    protected transient S owner;

    public void setOwner(S owner) {
        this.owner = owner;
    }

    public Descriptor<SlaveProperty<?>> getDescriptor() {
        return (SlavePropertyDescriptor)Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public SlaveProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return form==null ? null : getDescriptor().newInstance(req, form);
    }

    public abstract Collection<? extends Action> getSlaveActions(Slave m);

    /**
     * Lists up all the registered {@link metanectar.model.ConnectedMasterPropertyDescriptor}s in the system.
     */
    public static DescriptorExtensionList<SlaveProperty<?>,SlavePropertyDescriptor> all() {
        return (DescriptorExtensionList) Hudson.getInstance().getDescriptorList(SlaveProperty.class);
    }

    /**
     * List up all {@link metanectar.model.ConnectedMasterPropertyDescriptor}s that are applicable for the
     * given connected master.
     */
    public static List<SlavePropertyDescriptor> for_(Slave node) {
        return SlavePropertyDescriptor.for_(all(), node);
    }

}
