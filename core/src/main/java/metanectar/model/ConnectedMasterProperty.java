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
public abstract class ConnectedMasterProperty<S extends ConnectedMaster> implements ReconfigurableDescribable<ConnectedMasterProperty<?>>, ExtensionPoint {
    protected transient S owner;

    public void setOwner(S owner) {
        this.owner = owner;
    }

    public Descriptor<ConnectedMasterProperty<?>> getDescriptor() {
        return (ConnectedMasterPropertyDescriptor)Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public ConnectedMasterProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return form==null ? null : getDescriptor().newInstance(req, form);
    }

    public abstract Collection<? extends Action> getConnectedMasterActions(ConnectedMaster m);

    /**
     * Lists up all the registered {@link ConnectedMasterPropertyDescriptor}s in the system.
     */
    public static DescriptorExtensionList<ConnectedMasterProperty<?>,ConnectedMasterPropertyDescriptor> all() {
        return (DescriptorExtensionList) Hudson.getInstance().getDescriptorList(ConnectedMasterProperty.class);
    }

    /**
     * List up all {@link ConnectedMasterPropertyDescriptor}s that are applicable for the
     * given connected master.
     */
    public static List<ConnectedMasterPropertyDescriptor> for_(ConnectedMaster node) {
        return ConnectedMasterPropertyDescriptor.for_(all(), node);
    }

}
