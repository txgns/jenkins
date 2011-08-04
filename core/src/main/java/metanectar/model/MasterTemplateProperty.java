package metanectar.model;

import hudson.ExtensionPoint;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ReconfigurableDescribable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
public abstract class MasterTemplateProperty implements
        ReconfigurableDescribable<MasterTemplateProperty>, ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(MasterTemplateProperty.class.getName());

    protected transient MasterTemplate owner;

    public void setOwner(MasterTemplate owner) {
        this.owner = owner;
    }

    public MasterTemplatePropertyDescriptor getDescriptor() {
        return (MasterTemplatePropertyDescriptor)Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public MasterTemplateProperty reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return form==null ? null : getDescriptor().newInstance(req, form);
    }

    public abstract Collection<? extends Action> getMasterTemplateActions(MasterTemplate m);

    /**
     * Lists up all the registered {@link metanectar.model.MasterTemplatePropertyDescriptor}s in the system.
     */
    public static List<MasterTemplatePropertyDescriptor>  all() {
        return Hudson.getInstance().getDescriptorList(MasterTemplateProperty.class);
    }

    /**
     * List up all {@link metanectar.model.MasterTemplatePropertyDescriptor}s that are applicable for the
     * given connected master.
     */
    public static List<MasterTemplatePropertyDescriptor> for_(MasterTemplate node) {
        return ConnectedMasterPropertyDescriptor.for_(all(), node);
    }

}
