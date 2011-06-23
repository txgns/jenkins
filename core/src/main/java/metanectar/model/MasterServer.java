package metanectar.model;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.*;
import hudson.util.DescribableList;
import metanectar.Config;
import metanectar.provisioning.IdentifierFinder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static metanectar.model.MasterServer.State.*;

/**
 * A managed and provisioned master.
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
 */
public class MasterServer extends ConnectedMaster {

    /**
     * The states of the master.
     */
    public static enum State {
        Created(Action.Provision, Action.Delete),
        PreProvisioning(),
        Provisioning(),
        ProvisioningErrorNoResources(), // TODO delete, need remove from pending requests
        ProvisioningError(Action.Provision, Action.Terminate),
        Provisioned(Action.Start, Action.Terminate),
        Starting(),
        StartingError(Action.Start, Action.Stop),
        Started(Action.Stop),
        ApprovalError(Action.Stop),
        Approved(Action.Stop),
        Stopping(),
        StoppingError(Action.Stop, Action.Terminate),
        Stopped(Action.Start, Action.Terminate),
        Terminating(),
        TerminatingError(Action.Terminate, Action.Delete),
        Terminated(Action.Provision, Action.Delete);

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
        Provision("new-computer.png"),
        Start("start-computer.png"),
        Stop("stop-computer.png"),
        Terminate("terminate-computer.png"),
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

    /**
     * The global URL to the master. May be null if no reverse proxy is utilized.
     */
    protected transient volatile URL globalEndpoint;

    /**
     * The name of the node where the master is provisioned
     */
    private volatile String nodeName;

    /**
     * The node where this masters is provisioned.
     * <p>
     * Only the node name is serialized.
     */
    private transient volatile Node node;

    /**
     * A unique number that is always less than the total number of masters
     * provisioned for a node.
     */
    private volatile int nodeId;

    /**
     * The URL pointing to the snapshot of the master home directory.
     */
    private volatile URL snapshot;


    protected MasterServer(ItemGroup parent, String name) {
        super(parent, name);
    }


    //

    public String toString() {
        return toStringHelper().
                add("nodeName", nodeName).
                add("node", getNode()).
                add("nodeId", nodeId).
                add("snapshot", snapshot).
                add("globalEndpoint", globalEndpoint).
                add("state", state).
                toString();
    }


    // Methods for modifying state

    @Override
    public synchronized void setCreatedState() throws IOException {
        setState(Created);

        fireOnStateChange();

        taskListener.getLogger().println("Created");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setPreProvisionState() throws IOException {
        setState(PreProvisioning);
        this.grantId = createGrant();
        save();
        fireOnStateChange();

        taskListener.getLogger().println("PreProvisioning");
        taskListener.getLogger().println(toString());
    }

    public synchronized void cancelPreProvisionState() throws IOException {
        setState(Created);
        this.grantId = null;
        save();

        taskListener.getLogger().println("Cancelled PreProvisioning");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setProvisionStartedState(Node node, int id) throws IOException {
        setState(Provisioning);
        this.nodeName = node.getNodeName();
        this.node = node;
        this.nodeId = id;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Provisioning");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setProvisionCompletedState(String home, URL endpoint) throws IOException {
        setState(Provisioned);
        this.localHome = home;
        this.localEndpoint = endpoint;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Provisioned");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setProvisionErrorState(Throwable error) throws IOException {
        setState(ProvisioningError);
        this.error = error;
        this.nodeId = 0;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Provision Error");
        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Provision Error"));
    }

    public synchronized void setProvisionErrorNoResourcesState() throws IOException {
        if (state != ProvisioningErrorNoResources) {
            setState(ProvisioningErrorNoResources);
            save();
            fireOnStateChange();

            taskListener.getLogger().println("Provision Error No Resources");
            taskListener.getLogger().println(toString());
        }
    }

    public synchronized void setStartingState() throws IOException {
        setState(Starting);
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Starting");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setStartingErrorState(Throwable error) throws IOException {
        setState(StartingError);
        this.error = error;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Starting Error");
        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Starting Error"));
    }

    public synchronized void setStartedState() throws IOException {
        // Potentially may go from the starting state to the approved state
        // if the master communicates with MetaNectar before the periodic timer executes
        // to process the completion of the start task
        if (this.state == Starting) {
            setState(Started);
            save();
            fireOnStateChange();
        }

        taskListener.getLogger().println("Started");
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

    public synchronized void setStoppingState() throws IOException {
        setState(Stopping);
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Stopping");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setStoppingErrorState(Throwable error) throws IOException {
        setState(StoppingError);
        this.error = error;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Stopping Error");
        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Stopping Error"));
    }

    public synchronized void setStoppedState() throws IOException {
        setState(Stopped);
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Stopped");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setTerminateStartedState() throws IOException {
        if (isOnline()) {
            this.channel.close();
        }

        setState(Terminating);
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Terminating");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setTerminateErrorState(Throwable error) throws IOException {
        setState(TerminatingError);
        this.error = error;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Terminating Error");
        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Terminating Error"));
    }

    public synchronized void setTerminateCompletedState(URL snapshot) throws IOException {
        setState(Terminated);
        this.grantId = null;
        this.approved = false;
        this.nodeName = null;
        this.node = null;
        this.nodeId = 0;
        this.localHome = null;
        this.localEndpoint = null;
        this.globalEndpoint = null;
        this.identity = null;

        // Remove the old snapshot if present
        if (this.snapshot != null) {
            File f = new File(this.snapshot.getPath());
            f.delete();
        }
        this.snapshot = snapshot;

        save();
        fireOnStateChange();

        taskListener.getLogger().println("Terminated");
        taskListener.getLogger().println(toString());
    }

    private void setState(State state) {
        this.state = state;
        this.error = null;
        this.timeStamp = new Date().getTime();
    }


    // Event firing

    private final void fireOnStateChange() {
        MasterServerListener.fireOnStateChange(this);
    }


    // State querying

    @Override
    public boolean isApprovable() {
        switch (state) {
            case Starting:
            case Started:
            case Approved:
                return true;
            default:
                return false;
        }
    }

    public boolean isProvisioned() {
        return state.ordinal() >= Provisioned.ordinal() && state.ordinal() < Terminated.ordinal();
    }

    public boolean isTerminating() {
        return state.ordinal() > Stopped.ordinal();
    }

    public boolean isTerminatingError() {
        return state == TerminatingError;
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

    public boolean canProvisionAction() {
        return canDoAction(Action.Provision);
    }

    public boolean canStartAction() {
        return canDoAction(Action.Start);
    }

    public boolean canStopAction() {
        return canDoAction(Action.Stop);
    }

    public boolean canTerminateAction() {
        return canDoAction(Action.Terminate);
    }

    public boolean canDeleteAction() {
        return canDoAction(Action.Delete);
    }

    private void preConditionAction(Action a) throws AbortException {
        if (!canDoAction(a)) {
            throw new AbortException(String.format("Action \"%s\" cannot be performed when in state \"\"", a.name(), getState().name()));
        }
    }

    public synchronized void provisionAndStartAction() throws IOException  {
        preConditionAction(Action.Provision);

        Map<String, Object> properties = new HashMap<String, Object>();
        MetaNectar.getInstance().masterProvisioner.provisionAndStart(this, MetaNectar.getInstance().getMetaNectarPortUrl(), properties);
    }

    public synchronized void stopAndTerminateAction() throws IOException {
        preConditionAction(Action.Stop);

        MetaNectar.getInstance().masterProvisioner.stopAndTerminate(this);
    }

    public synchronized void provisionAction() throws IOException  {
        preConditionAction(Action.Provision);

        Map<String, Object> properties = new HashMap<String, Object>();
        MetaNectar.getInstance().masterProvisioner.provision(this, MetaNectar.getInstance().getMetaNectarPortUrl(),
                properties);
    }

    public synchronized boolean cancelProvisionAction() throws IOException {
        if (state != State.PreProvisioning && state != State.ProvisioningErrorNoResources) {
            throw new AbortException(String.format("Cancel of a provision request cannot be performed when in state \"\"", getState().name()));
        }

        return MetaNectar.getInstance().masterProvisioner.cancelPendingRequest(this);
    }

    public synchronized void startAction() throws IOException {
        preConditionAction(Action.Start);

        MetaNectar.getInstance().masterProvisioner.start(this);
    }

    public synchronized void stopAction() throws IOException {
        preConditionAction(Action.Stop);

        MetaNectar.getInstance().masterProvisioner.stop(this);
    }

    public synchronized void terminateAction(boolean force) throws IOException {
        preConditionAction(Action.Terminate);

        MetaNectar.getInstance().masterProvisioner.terminate(this, force);
    }

    @Override
    public synchronized void delete() throws IOException, InterruptedException {
        preConditionAction(Action.Delete);

        if (state == MasterServer.State.TerminatingError) {
            // TODO disable this, or only enable for development purposes.
            setTerminateCompletedState(null);
        }

        super.delete();
    }

    // Methods for accessing state

    public State getState() {
        return state;
    }

    public URL getEndpoint() {
        return (getGlobalEndpoint() != null) ? getGlobalEndpoint() : getLocalEndpoint();
    }

    public synchronized URL getGlobalEndpoint() {
        if (globalEndpoint == null) {
            if (getLocalEndpoint() == null)
                return null;

            try {
                globalEndpoint = createGlobalEndpoint(getLocalEndpoint());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error creating global endpoint", e);
                globalEndpoint = localEndpoint;
            }
        }
        return globalEndpoint;
    }

    public String getStatePage() {
        return state.name().toLowerCase();
    }

    public URL getSnapshot() {
        return snapshot;
    }

    public Node getNode() {
        if (node == null) {
            if (nodeName == null)
                return null;

            node = (nodeName.isEmpty()) ? MetaNectar.getInstance() : MetaNectar.getInstance().getNode(nodeName);
        }

        return node;
    }

    public int getNodeId() {
        return nodeId;
    }


    @Override
    protected void setDisconnectStateCallback(Throwable error) throws IOException {
        // Ignore the error if already disconnected due to state change
        if (state.ordinal() > Approved.ordinal()) {
            super.setDisconnectStateCallback();
        } else {
            super.setDisconnectStateCallback(error);
        }
    }


    // UI actions

    public HttpResponse doProvisionAction() throws Exception {
        return new DoActionLambda() {
            public void f() throws Exception {
                provisionAction();
            }
        }.doAction();
    }

    public HttpResponse doCancelProvisionAction() throws Exception {
        return new DoActionLambda() {
            public void f() throws Exception {
                cancelProvisionAction();
            }
        }.doAction();
    }

    public HttpResponse doStartAction() throws Exception {
        return new DoActionLambda() {
            public void f() throws Exception {
                startAction();
            }
        }.doAction();
    }

    public HttpResponse doStopAction() throws Exception {
        return new DoActionLambda() {
            public void f() throws Exception {
                stopAction();
            }
        }.doAction();
    }

    public HttpResponse doTerminateAction(final @QueryParameter String force) throws Exception {
        return new DoActionLambda() {
            public void f() throws Exception {
                if (isTerminatingError() && force != null) {
                    terminateAction(true);
                } else {
                    terminateAction(false);
                }
            }
        }.doAction();
    }

    private abstract class DoActionLambda {
        abstract void f() throws Exception;

        HttpResponse doAction() throws Exception {
            requirePOST();

            f();

            return HttpResponses.redirectToDot();
        }
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

    //////// Methods to handle the weather icon

    /**
     * Get the current health report for a job.
     *
     * @return the health report. Never returns null
     */
    public HealthReport getBuildHealth() {
        List<HealthReport> reports = getBuildHealthReports();
        return reports.isEmpty() ? new HealthReport(100, Messages._SharedCloud_PerfectHealth()) : reports.get(0);
    }

    @Exported(name = "healthReport")
    public List<HealthReport> getBuildHealthReports() {
        if (getState().name().toLowerCase().contains("error")) {
            return Arrays.asList(new HealthReport(0, getState().name()));
        } else if (State.Approved.equals(getState())) {
        return Arrays.asList(new HealthReport(100, Messages._SharedCloud_PerfectHealth()));
        } else if (State.Starting.equals(getState())) {
            return Arrays.asList(new HealthReport(75, Messages._SharedCloud_PerfectHealth()));
        } else if (State.Provisioned.equals(getState())) {
            return Arrays.asList(new HealthReport(45, Messages._SharedCloud_PerfectHealth()));
        }
        return Arrays.asList(new HealthReport(25, Messages._SharedCloud_PerfectHealth()));
    }


    //

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return "Managed Master";
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new MasterServer(parent, name);
        }

        public DescribableList<ConnectedMasterProperty,ConnectedMasterPropertyDescriptor> getDefaultProperties() throws IOException {
            return new DescribableList<ConnectedMasterProperty,ConnectedMasterPropertyDescriptor>(NOOP);
        }

    }

    /**
     * Create the global endpoint if a reverse proxy is deployed.
     *
     * @param localEndpoint the local endpoint
     * @return the global endpoint, otherwise the local endpoint.
     */
    public static URL createGlobalEndpoint(URL localEndpoint) throws IOException {
        Config.ProxyProperties p = MetaNectar.getInstance().getConfig().getBean(Config.ProxyProperties.class);
        if (p.getBaseEndpoint() != null) {
            URL proxyEndpoint = p.getBaseEndpoint();

            // This assumes that the paths for both URLs start with "/"
            String path = proxyEndpoint.getPath() + localEndpoint.getPath();
            path = path.replaceAll("/+", "/");
            return new URL(proxyEndpoint.getProtocol(), proxyEndpoint.getHost(), proxyEndpoint.getPort(), path);
        } else {
            return localEndpoint;
        }
    }

    public static IdentifierFinder NODE_IDENTIFIER_FINDER = new IdentifierFinder<MasterServer>() {
        public int getId(MasterServer ms) {
            return ms.getNodeId();
        }
    };

    private static final Logger LOGGER = Logger.getLogger(MasterServer.class.getName());
}
