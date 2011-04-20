package metanectar.model;

import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import com.google.common.base.Objects;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static metanectar.model.MasterServer.State.*;

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
     * <p>
     */
    public static enum State {
        Created,
        PreProvisioning,
        Provisioning,
        ProvisioningErrorNoResources,
        ProvisioningError,
        Provisioned,
        Connectable,
        PreTerminating,
        Terminating,
        TerminatingError,
        Terminated
    }

    /**
     * The state of the master.
     */
    private volatile State state;

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
                add("error", error).
                add("grantId", grantId).
                add("approved", approved).
                add("nodeName", nodeName).
                add("node", getNode()).
                add("id", id).
                add("endpoint", endpoint).
                add("channel", channel).
                add("identity", key.getFormat() + ", " + key.getAlgorithm()).
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

    public void setCreatedState(String grantId) throws IOException {
        setState(Created);
        this.grantId = grantId;
        save();
        fireOnCreated();

        taskListener.getLogger().println("Created");
        taskListener.getLogger().println(toString());
    }

    public void setPreProvisionState() throws IOException {
        setState(PreProvisioning);
        save();

        taskListener.getLogger().println("PreProvisioning");
        taskListener.getLogger().println(toString());
    }

    public void setProvisionStartedState(Node node, int id) throws IOException {
        setState(Provisioning);
        this.nodeName = node.getNodeName();
        this.node = node;
        this.id = id;
        save();
        fireOnProvisioning();

        taskListener.getLogger().println("Provisioning");
        taskListener.getLogger().println(toString());
    }

    public void setProvisionCompletedState(Node node, URL endpoint) throws IOException {
        // Potentially may go from the provisioning state to the disconnected state
        // if the master communicates with nectar before the periodic timer executes
        // to process the provisioning event
        if (this.state == Provisioning) {
            setState(Provisioned);
            this.nodeName = node.getNodeName();
            this.node = node;
            this.endpoint = endpoint;
            save();
            fireOnProvisioned();
        }

        taskListener.getLogger().println("Provisioned");
        taskListener.getLogger().println(toString());
    }

    public void setProvisionErrorState(Node node, Throwable error) throws IOException {
        setState(ProvisioningError);
        this.error = error;
        this.nodeName = node.getNodeName();
        this.node = node;
        this.id = 0;
        save();
        fireOnProvisioningError();

        taskListener.getLogger().println("Provision Error");
        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Provision Error"));
    }

    public void setProvisionErrorNoResourcesState() throws IOException {
        if (state != ProvisioningErrorNoResources) {
            setState(ProvisioningErrorNoResources);
            save();
            fireOnProvisioningErrorNoResources();

            taskListener.getLogger().println("Provision Error No Resources");
            taskListener.getLogger().println(toString());
        }
    }

    public void setApprovedState(RSAPublicKey pk, URL endpoint) throws IOException {
        setState(Connectable);
        this.identity = pk.getEncoded();
        this.endpoint = endpoint;
        this.approved = true;
        save();
        fireOnApproved();

        taskListener.getLogger().println("Approved");
        taskListener.getLogger().println(toString());
    }

    public void setConnectedState(Channel channel) throws IOException {
        if (!setChannel(channel))
            return;

        this.error = null;
        fireOnConnected();

        channel.setProperty(SlaveManager.class.getName(),
                channel.export(SlaveManager.class, new MetaNectarSlaveManager()));

        taskListener.getLogger().println("Connected");
        taskListener.getLogger().println(toString());
    }

    public void setPreTerminateState() throws IOException {
        setState(PreTerminating);
        save();

        taskListener.getLogger().println("PreTerminating");
        taskListener.getLogger().println(toString());
    }

    public void setTerminateStartedState() throws IOException {
        if (isOnline()) {
            this.channel.close();
        }

        setState(Terminating);
        save();
        fireOnTerminating();

        taskListener.getLogger().println("Terminating");
        taskListener.getLogger().println(toString());
    }

    public void setTerminateCompletedState() throws IOException {
        setState(Terminated);
        this.node = null;
        this.nodeName = null;
        this.endpoint = null;
        this.id = 0;
        save();
        fireOnTerminated();

        taskListener.getLogger().println("Terminated");
        taskListener.getLogger().println(toString());
    }

    public void setTerminateErrorState(Throwable error) throws IOException {
        setState(TerminatingError);
        this.error = error;
        save();
        fireOnTerminatingError();

        taskListener.getLogger().println("Terminating Error");
        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Terminating Error"));
    }

    private void setState(State state) {
        this.state = state;
        this.error = null;
    }

    // Event firing

    private final void fireOnCreated() {
        fire (new Lambda() {
            public void f(MasterServerListener msl) {
                msl.onCreated(MasterServer.this);
            }
        });
    }

    private final void fireOnProvisioning() {
        fire (new Lambda() {
            public void f(MasterServerListener msl) {
                msl.onProvisioning(MasterServer.this);
            }
        });
    }

    final void fireOnProvisioningErrorNoResources() {
        fire (new Lambda() {
            public void f(MasterServerListener msl) {
                msl.onProvisioningErrorNoResources(MasterServer.this);
            }
        });
    }

    private final void fireOnProvisioningError() {
        fire (new Lambda() {
            public void f(MasterServerListener msl) {
                msl.onProvisioningError(MasterServer.this, MasterServer.this.getNode());
            }
        });
    }

    private final void fireOnProvisioned() {
        fire (new Lambda() {
            public void f(MasterServerListener msl) {
                msl.onProvisioned(MasterServer.this);
            }
        });
    }

    private final void fireOnApproved() {
        fire (new Lambda() {
            public void f(MasterServerListener msl) {
                msl.onApproved(MasterServer.this);
            }
        });
    }

    private final void fireOnConnected() {
        fire (new Lambda() {
            public void f(MasterServerListener msl) {
                msl.onConnected(MasterServer.this);
            }
        });
    }

    private final void fireOnDisconnected() {
        fire (new Lambda() {
            public void f(MasterServerListener msl) {
                msl.onDisconnected(MasterServer.this);
            }
        });
    }

    private final void fireOnTerminating() {
        fire (new Lambda() {
            public void f(MasterServerListener msl) {
                msl.onTerminating(MasterServer.this);
            }
        });
    }

    private final void fireOnTerminatingError() {
        fire (new Lambda() {
            public void f(MasterServerListener msl) {
                msl.onTerminatingError(MasterServer.this);
            }
        });
    }

    private final void fireOnTerminated() {
        fire (new Lambda() {
            public void f(MasterServerListener msl) {
                msl.onTerminated(MasterServer.this);
            }
        });
    }


    private interface Lambda {
        void f(MasterServerListener msl);
    }

    private void fire(Lambda l) {
        for (MasterServerListener msl : MasterServerListener.all()) {
            try {
                l.f(msl);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Exception when firing event", e);
            }
        }
    }

    // Methods for accessing state

    public State getState() {
        return state;
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

    public boolean canTerminate() {
        switch (state) {
            case ProvisioningError:
            case Provisioned:
            case Connectable:
            case TerminatingError:
                return true;

            default:
                return false;
        }
    }

    public boolean canDelete() {
        switch (state) {
            case TerminatingError:
            case Terminated:
                return true;

            default:
                return false;
        }
    }

    public boolean isTerminating() {
        return state.ordinal() > PreTerminating.ordinal();
    }

    //

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
            fireOnDisconnected();

            taskListener.getLogger().println("Disconnected Error");
            taskListener.getLogger().println(toString());
            error.printStackTrace(taskListener.error("Disconnected Error"));
        }
    }


    public Map<String,String> getThreadDump() throws IOException, InterruptedException {
        return RemotingDiagnostics.getThreadDump(getChannel());
    }


    //

    public void terminate(boolean clean) throws IOException {
        MetaNectar.getInstance().masterProvisioner.terminate(this, clean);
    }

    public HttpResponse doDoTerminate() throws Exception  {
        requirePOST();

        if (!canTerminate()) {
            // TODO better error
            throw new IOException("Cannot terminate");
        }

        terminate(false);

        return HttpResponses.redirectToDot();

    }

    public HttpResponse doDisconnect() throws Exception {
        requirePOST();

        this.channel.close();

        taskListener.getLogger().println("Disconnecting");
        taskListener.getLogger().println(toString());
        return HttpResponses.redirectToDot();
    }

    public synchronized void doConfigSubmit(StaplerRequest req,
            StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");
        save();
    }

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        HttpResponses.redirectViaContextPath(getUrl()).generateResponse(req, rsp, node);
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
