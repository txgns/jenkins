package metanectar.model;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.util.DescribableList;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static metanectar.model.AttachedMaster.State.*;

/**
 * An attached master that is not provisioned by MetaNectar.
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
 */
public class AttachedMaster extends ConnectedMaster {

    /**
     * The states of the master.
     */
    public static enum State {
        Created(Action.Delete),
        ApprovalError(Action.Delete),
        Approved(Action.Delete);

        public ImmutableSet<Action> actions;

        State(Action... actions) {
            this.actions = new ImmutableSet.Builder<Action>().add(actions).build();
        }

        public boolean canDo(Action a) {
            return actions.contains(a);
        }
    }

    /**
     * Actions that can be performed on a master.
     */
    public static enum Action {
        Delete("trash-computer.png");

        public final String icon;

        public final String displayName;

        public final String href;

        Action(String icon) {
            this.icon = icon;
            this.displayName = name();
            this.href = name().toLowerCase();
        }

        Action(String icon, String displayName) {
            this.icon = icon;
            this.displayName = displayName;
            this.href = name().toLowerCase();
        }
    }

    /**
     * The state of the master.
     */
    private volatile State state;


    protected AttachedMaster(ItemGroup parent, String name) {
        super(parent, name);
    }

    //

    public String toString() {
        return toStringHelper().toString();
    }


    // Methods for modifying state

    @Override
    public synchronized void setCreatedState() throws IOException {
        setState(Created);
        this.grantId = createGrant();

        fireOnStateChange();

        taskListener.getLogger().println("Created");
        taskListener.getLogger().println(toString());
    }


    @Override
    public synchronized void setApprovedState(RSAPublicKey pk, URL endpoint) throws IOException {
        setState(Approved);
        this.identity = pk.getEncoded();
        this.localEndpoint = endpoint;
        this.approved = true;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Approved");
        taskListener.getLogger().println(toString());
    }

    @Override
    public synchronized void setReapprovedState() throws IOException {
        if (state == State.Approved)
            return;

        if (identity == null || localEndpoint == null || approved == false)
            throw new IllegalStateException();

        setState(Approved);
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Approved");
        taskListener.getLogger().println(toString());
    }

    @Override
    public synchronized void setApprovalErrorState(Throwable error) throws IOException {
        setState(ApprovalError);
        this.error = error;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Approval Error");
        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Approval Error"));
    }

    private void setState(State state) {
        this.state = state;
        this.error = null;
        this.timeStamp = new Date().getTime();
    }

    // Event firing

    private final void fireOnStateChange() {
        AttachedMasterListener.fireOnStateChange(this);
    }


    // State querying

    public boolean isApprovable() {
        switch (state) {
            case Created:
            case Approved:
            case ApprovalError:
                return true;
            default:
                return false;
        }
    }

    // Actions

    public Set<Action> getActionSet() {
        return ImmutableSet.copyOf(Action.values());
    }

    public ImmutableSet<Action> getValidActionSet() {
        return getState().actions;
    }

    public boolean canDoAction(Action a) {
        return state.canDo(a);
    }

    public boolean canDeleteAction() {
        return canDoAction(Action.Delete);
    }

    private void preConditionAction(Action a) throws IllegalStateException {
        if (!canDoAction(a)) {
            throw new IllegalStateException(String.format("Action \"%s\" cannot be performed when in state \"\"", a.name(), getState().name()));
        }
    }

    @Override
    public synchronized void delete() throws IOException, InterruptedException {
        if (isOnline()) {
            try {
                channel.close();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Error closing channel to attached master.");
            }
        }
        super.delete();
    }

    // Methods for accessing state

    public State getState() {
        return state;
    }

    public String getStatePage() {
        return state.name().toLowerCase();
    }


    // Configuration

    public synchronized void doConfigSubmit(StaplerRequest req,
            StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");

        JSONObject json = req.getSubmittedForm();

        DescribableList<ConnectedMasterProperty, ConnectedMasterPropertyDescriptor> t =
                new DescribableList<ConnectedMasterProperty, ConnectedMasterPropertyDescriptor>(NOOP,getProperties().toList());
        t.rebuild(req,json.optJSONObject("properties"),ConnectedMasterProperty.all());
        properties.clear();
        for (ConnectedMasterProperty p : t) {
            p.setOwner(this);
            properties.add(p);
        }

        save();

        rsp.sendRedirect(".");
    }


    //

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return "Attached Master";
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new AttachedMaster(parent, name);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(AttachedMaster.class.getName());
}
