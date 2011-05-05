package metanectar.model;

import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.model.*;
import hudson.remoting.Channel;
import hudson.util.RemotingDiagnostics;
import hudson.util.StreamTaskListener;
import hudson.util.io.ReopenableFileOutputStream;
import metanectar.provisioning.MetaNectarSlaveManager;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static metanectar.model.MasterServer.State.*;
import static metanectar.model.MasterServer.State.Approved;

/**
 * Representation of remote Master server inside MetaNectar.
 *
 * TODO construct vanity URL
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
 */
public class MasterServer extends AbstractItem implements TopLevelItem, HttpResponse {

    /**
     * The states of the master.
     */
    public static enum State {
        Created(Action.Provision),
        PreProvisioning(),
        Provisioning(),
        ProvisioningErrorNoResources(),   // TODO cancel
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
        Terminate("edit-delete.gif"),
        Delete("edit-delete.gif");

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
     * The time stamp when the state was modified.
     *
     * @see {@link java.util.Date#getTime()}.
     */
    private volatile long timeStamp;

    /**
     * Error associated with a particular state.
     */
    private transient volatile Throwable error;

    /**
     * The grant ID for the master to validate when initially connecting.
     */
    private String grantId;

    /**
     * If the connection to this Jenkins is approved, set to true.
     */
    private volatile boolean approved;

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
     * A unique number that is always less than or equal to the total number of masters
     * provisioned for a node.
     */
    private int id;

    /**
     * The direct URL to the master.
     */
    private volatile URL endpoint;

    /**
     * The clean URL to the master that is server through the reverse proxy, if any,
     * otherwise the same as <code>endpoint</code>.
     */
    private volatile URL vanityEndpoint;

    /**
     * The encoded image of the public key that indicates the identity of the masters.
     */
    private volatile byte[] identity;

    // connected state

    private transient /* final */ Object channelLock = new Object();

    private transient volatile Channel channel;

    /**
     * Perpetually writable log file.
     */
    private transient ReopenableFileOutputStream log;

    /**
     * {@link TaskListener} that wraps {@link #log}, hence perpetually writable.
     */
    private transient TaskListener taskListener;

    protected MasterServer(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name)
            throws IOException {
        super.onLoad(parent, name);
        init();
    }

    @Override
    public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        init();
    }

    private void init() {
        log = new ReopenableFileOutputStream(getLogFile());
        taskListener = new StreamTaskListener(log);
        channelLock = new Object();
    }

    private File getLogFile() {
        return new File(getRootDir(),"log.txt");
    }

    public String toString() {
        final RSAPublicKey key = getIdentity();
        return Objects.toStringHelper(this).
                add("state", state).
                add("timeStamp", timeStamp).
                add("error", error).
                add("grantId", grantId).
                add("approved", approved).
                add("nodeName", nodeName).
                add("node", getNode()).
                add("id", id).
                add("endpoint", endpoint).
                add("channel", channel).
                add("identity", (key == null) ? null : key.getFormat() + ", " + key.getAlgorithm()).
                toString();
    }

    /**
     * No nested job under Jenkins server
     *
     * @deprecated
     *      No one shouldn't be calling this directly.
     */
    @Override
    public final Collection<? extends Job> getAllJobs() {
        return Collections.emptyList();
    }

    public TopLevelItemDescriptor getDescriptor() {
        return (TopLevelItemDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }


    // Methods for modifying state

    public synchronized void setCreatedState() throws IOException {
        setState(Created);
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Created");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setPreProvisionState() throws IOException {
        setState(PreProvisioning);
        this.grantId = createGrant();
        save();

        taskListener.getLogger().println("PreProvisioning");
        taskListener.getLogger().println(toString());
    }

    private String createGrant() {
        return UUID.randomUUID().toString();
    }

    public synchronized void setProvisionStartedState(Node node, int id) throws IOException {
        setState(Provisioning);
        this.nodeName = node.getNodeName();
        this.node = node;
        this.id = id;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Provisioning");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setProvisionCompletedState(Node node, URL endpoint) throws IOException {
        setState(Provisioned);
        this.nodeName = node.getNodeName();
        this.node = node;
        this.endpoint = endpoint;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Provisioned");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setProvisionErrorState(Node node, Throwable error) throws IOException {
        setState(ProvisioningError);
        this.error = error;
        this.nodeName = node.getNodeName();
        this.node = node;
        this.id = 0;
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

    public synchronized void setApprovedState(RSAPublicKey pk, URL endpoint) throws IOException {
        setState(Approved);
        this.identity = pk.getEncoded();
        this.endpoint = endpoint;
        this.approved = true;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Approved");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setReapprovedState() throws IOException {
        if (state == State.Approved)
            return;

        if (identity == null || endpoint == null || approved == false)
            throw new IllegalStateException();

        setState(Approved);
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Approved");
        taskListener.getLogger().println(toString());
    }

    public synchronized void setApprovalErrorState(Throwable error) throws IOException {
        setState(ApprovalError);
        this.error = error;
        save();
        fireOnStateChange();

        taskListener.getLogger().println("Approval Error");
        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Approval Error"));
    }

    public synchronized void setConnectedState(Channel channel) throws IOException {
        if (!setChannel(channel))
            return;

        this.error = null;
        fireOnConnected();

        channel.setProperty(SlaveManager.class.getName(),
                channel.export(SlaveManager.class, new MetaNectarSlaveManager()));

        taskListener.getLogger().println("Connected");
        taskListener.getLogger().println(toString());
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

    public synchronized void setTerminateCompletedState() throws IOException {
        setState(Terminated);
        this.grantId = null;
        this.approved = false;
        this.nodeName = null;
        this.node = null;
        this.id = 0;
        this.endpoint = null;
        this.identity = null;
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
        fire (new FireLambda() {
            public void f(MasterServerListener msl) {
                msl.onStateChange(MasterServer.this);
            }
        });
    }

    private final void fireOnConnected() {
        fire (new FireLambda() {
            public void f(MasterServerListener msl) {
                msl.onConnected(MasterServer.this);
            }
        });
    }

    private final void fireOnDisconnected() {
        fire (new FireLambda() {
            public void f(MasterServerListener msl) {
                msl.onDisconnected(MasterServer.this);
            }
        });
    }

    private interface FireLambda {
        void f(MasterServerListener msl);
    }

    private void fire(FireLambda l) {
        for (MasterServerListener msl : MasterServerListener.all()) {
            try {
                l.f(msl);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception when firing event", e);
            }
        }
    }


    // State querying

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

    public boolean isTerminating() {
        return state.ordinal() > Stopped.ordinal();
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

    private void preConditionAction(Action a) throws IllegalStateException {
        if (!canDoAction(a)) {
            throw new IllegalStateException(String.format("Action \"%s\" cannot be performed when in state \"\"", a.name(), getState().name()));
        }
    }

    public synchronized void provisionAndStartAction() throws IOException, IllegalStateException  {
        preConditionAction(Action.Provision);

        Map<String, Object> properties = new HashMap<String, Object>();
        MetaNectar.getInstance().masterProvisioner.provisionAndStart(this, MetaNectar.getInstance().getMetaNectarPortUrl(), properties);
    }

    public synchronized void stopAndTerminateAction(boolean clean) throws IllegalStateException {
        preConditionAction(Action.Stop);

        MetaNectar.getInstance().masterProvisioner.stopAndTerminate(this, clean);
    }

    public synchronized void provisionAction() throws IOException, IllegalStateException  {
        preConditionAction(Action.Provision);

        Map<String, Object> properties = new HashMap<String, Object>();
        MetaNectar.getInstance().masterProvisioner.provision(this, MetaNectar.getInstance().getMetaNectarPortUrl(), properties);
    }

    public synchronized void startAction() throws IllegalStateException {
        preConditionAction(Action.Start);

        MetaNectar.getInstance().masterProvisioner.start(this);
    }

    public synchronized void stopAction() throws IllegalStateException {
        preConditionAction(Action.Stop);

        MetaNectar.getInstance().masterProvisioner.stop(this);
    }

    public synchronized void terminateAction(boolean clean) throws IllegalStateException {
        preConditionAction(Action.Terminate);

        MetaNectar.getInstance().masterProvisioner.terminate(this, clean);
    }


    // Methods for accessing state

    public State getState() {
        return state;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public Throwable getError() {
        return error;
    }

    public String getGrantId() {
        return grantId;
    }

    public final URL getEndpoint() {
        return endpoint;
    }

    public Node getNode() {
        if (node == null) {
            if (nodeName == null)
                return null;

            node = (nodeName.isEmpty()) ? MetaNectar.getInstance() : MetaNectar.getInstance().getNode(nodeName);
        }

        return node;
    }

    public int getId() {
        return id;
    }

    public synchronized RSAPublicKey getIdentity() {
        try {
            if (identity == null)
                return null;

            return (RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(identity));
        } catch (GeneralSecurityException e) {
            LOGGER.log(Level.WARNING, "Failed to load the key", identity);
            identity = null;
            return null;
        }
    }

    public boolean isApproved() {
        return approved;
    }

    public TaskListener getTaskListener() {
        return taskListener;
    }

    public Channel getChannel() {
        return channel;
    }

    public boolean isOnline() {
        return getChannel() != null;
    }

    public boolean isOffline() {
        return getChannel() == null;
    }

    public String getIcon() {
        if (!isApproved())
            return "computer-x.png";

        if(isOffline())
            return "computer-x.png";
        else
            return "computer.png";
    }

    public StatusIcon getIconColor() {
        String icon = getIcon();
        if (isOffline())  {
            return new StockStatusIcon(icon, Messages._JenkinsServer_Status_Offline());
        } else {
            return new StockStatusIcon(icon, Messages._JenkinsServer_Status_Online());
        }
    }


    // Channel methods

    private boolean setChannel(Channel channel) throws IOException, IllegalStateException {
        // update the data structure atomically to prevent others from seeing a channel that's not properly initialized yet
        synchronized (channelLock) {
            if(this.channel != null) {
                // check again. we used to have this entire method in a big sycnhronization block,
                // but Channel constructor blocks for an external process to do the connection
                // if CommandLauncher is used, and that cannot be interrupted because it blocks at InputStream.
                // so if the process hangs, it hangs the thread in a lock, and since Hudson will try to relaunch,
                // we'll end up queuing the lot of threads in a pseudo deadlock.
                // This implementation prevents that by avoiding a lock. HUDSON-1705 is likely a manifestation of this.
                channel.close();
                LOGGER.warning("Already connected");
                return false;
            }

            this.channel = channel;
        }

        this.channel.addListener(new Channel.Listener() {
            @Override
            public void onClosed(Channel c, IOException cause) {
                MasterServer.this.channel = null;

                // Orderly shutdown will have null exception
                try {
                    if (cause != null) {
                        MasterServer.this.setDisconnectStateCallback(cause);
                    } else {
                        MasterServer.this.setDisconnectStateCallback();
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        return true;
    }

    private void setDisconnectStateCallback() throws IOException {
        fireOnDisconnected();

        taskListener.getLogger().println("Disconnected");
        taskListener.getLogger().println(toString());
    }

    private void setDisconnectStateCallback(Throwable error) throws IOException {
        // Ignore the error if in the process of terminating
        if (state.ordinal() < Terminating.ordinal()) {
            setDisconnectStateCallback();
        } else {
            this.error = error;

            fireOnDisconnected();

            taskListener.getLogger().println("Disconnected Error");
            taskListener.getLogger().println(toString());
            error.printStackTrace(taskListener.error("Disconnected Error"));
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

    public HttpResponse doStartAction() throws Exception {
        return new DoActionLambda() {
            public void f() {
                startAction();
            }
        }.doAction();
    }

    public HttpResponse doStopAction() throws Exception {
        return new DoActionLambda() {
            public void f() {
                stopAction();
            }
        }.doAction();
    }

    public HttpResponse doTerminateAction() throws Exception {
        return new DoActionLambda() {
            public void f() {
                terminateAction(false);
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
        save();

        rsp.sendRedirect(".");
    }

    // Test stuff

    public HttpResponse doDisconnect() throws Exception {
        requirePOST();

        this.channel.close();

        taskListener.getLogger().println("Disconnecting");
        taskListener.getLogger().println(toString());
        return HttpResponses.redirectToDot();
    }


    public Map<String,String> getThreadDump() throws IOException, InterruptedException {
        return RemotingDiagnostics.getThreadDump(getChannel());
    }


    //

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        HttpResponses.redirectViaContextPath(getUrl()).generateResponse(req, rsp, node);
    }

    /**
     * Returns {@code true} if the page elements should be refreshed by AJAX.
     * @return {@code true} if the page elements should be refreshed by AJAX.
     */
    public boolean isAjaxPageRefresh() {
        return true; //TODO make decision
    }

    /**
     * Returns the number of seconds before the next AJAX refresh.
     * @return the number of seconds before the next AJAX refresh.
     */
    public int getPageRefreshDelay() {
        return isAjaxPageRefresh() ? 1 : 0;
    }

    public String getStatePage() {
        return state.name().toLowerCase();
    }

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return "Master server";
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new MasterServer(parent, name);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MasterServer.class.getName());
}
