package metanectar.model;

import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Action;
import hudson.model.Describable;
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
public abstract class ConnectedMasterProperty implements
        ReconfigurableDescribable<ConnectedMasterProperty>, ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(ConnectedMasterProperty.class.getName());

    protected transient ConnectedMaster owner;

    public void setOwner(ConnectedMaster owner) {
        this.owner = owner;
    }

    public ConnectedMasterPropertyDescriptor getDescriptor() {
        return (ConnectedMasterPropertyDescriptor)Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public ConnectedMasterProperty reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return form==null ? null : getDescriptor().newInstance(req, form);
    }

    public abstract Collection<? extends Action> getConnectedMasterActions(ConnectedMaster m);

    /**
     * Lists up all the registered {@link ConnectedMasterPropertyDescriptor}s in the system.
     */
    public static List<ConnectedMasterPropertyDescriptor>  all() {
        return Hudson.getInstance().getDescriptorList(ConnectedMasterProperty.class);
    }

    /**
     * List up all {@link ConnectedMasterPropertyDescriptor}s that are applicable for the
     * given connected master.
     */
    public static List<ConnectedMasterPropertyDescriptor> for_(ConnectedMaster node) {
        return ConnectedMasterPropertyDescriptor.for_(all(), node);
    }

    /**
     * Called when the master is connected.
     */
    public void onConnected() {}

    /**
     * Called when the master is disconnected.
     */
    public void onDisconnected() {}

    /* package */ static void fireOnConnected(final ConnectedMaster cm) {
        fire (cm, new FireLambda() {
            public void f(ConnectedMasterProperty property) {
                property.onConnected();
            }
        });
    }

    /* package */ static void fireOnDisconnected(final ConnectedMaster cm) {
        fire (cm, new FireLambda() {
            public void f(ConnectedMasterProperty property) {
                property.onDisconnected();
            }
        });
    }

    private interface FireLambda {
        void f(ConnectedMasterProperty cml);
    }

    private static void fire(ConnectedMaster cm, FireLambda l) {
        for (ConnectedMasterProperty property : cm.properties) {
            try {
                l.f(property);
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Exception when firing event", e);
            }
        }
    }

}
