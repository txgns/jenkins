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
 * Created by IntelliJ IDEA.
 * User: stephenc
 * Date: 09/05/2011
 * Time: 15:46
 * To change this template use File | Settings | File Templates.
 */
public abstract class MasterServerProperty<S extends MasterServer> implements ReconfigurableDescribable<MasterServerProperty<?>>, ExtensionPoint {
    protected transient S owner;

    public void setOwner(S owner) {
        this.owner = owner;
    }

    public Descriptor<MasterServerProperty<?>> getDescriptor() {
        return (MasterServerPropertyDescriptor)Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public MasterServerProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return form==null ? null : getDescriptor().newInstance(req, form);
    }

    public abstract Collection<? extends Action> getMasterServerActions(MasterServer job);

    /**
     * Lists up all the registered {@link MasterServerPropertyDescriptor}s in the system.
     */
    public static DescriptorExtensionList<MasterServerProperty<?>,MasterServerPropertyDescriptor> all() {
        return (DescriptorExtensionList) Hudson.getInstance().getDescriptorList(MasterServerProperty.class);
    }

    /**
     * List up all {@link MasterServerPropertyDescriptor}s that are applicable for the
     * given master server.
     */
    public static List<MasterServerPropertyDescriptor> for_(MasterServer node) {
        return MasterServerPropertyDescriptor.for_(all(),node);
    }

}
