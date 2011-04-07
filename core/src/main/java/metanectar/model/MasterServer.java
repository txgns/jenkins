package metanectar.model;

import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import com.google.common.base.Objects;
import hudson.Extension;
import hudson.model.*;
import hudson.remoting.Channel;
import hudson.slaves.OfflineCause;
import hudson.util.RemotingDiagnostics;
import hudson.util.StreamTaskListener;
import hudson.util.io.ReopenableFileOutputStream;
import metanectar.provisioning.MetaNectarSlaveManager;
import org.bouncycastle.asn1.cmp.ProtectedPart;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
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

/**
 * Representation of remote Master server inside MetaNectar.
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
 */
public class MasterServer extends AbstractItem implements TopLevelItem, HttpResponse {

    // Created, Provisioning, Provisioned, Connectable, Connected, Terminating, Terminated

    static enum State {
        Created,
        Provisioning,
        Provisioned,
        Connectable,
        Terminating,
        Terminated
    }

    /**
     * The state of the master.
     */
    protected volatile State state;

    /**
     * Error associated with a particular state.
     */
    protected transient volatile Throwable error;

    /**
     * The grant ID for the master to validat when initially connecting.
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
     * The URL to the master.
     */
    protected volatile URL endpoint;

    /**
     * The encoded image of the public key that indicates the identity of the masters.
     */
    private volatile byte[] identity;

    // connected state

    protected transient /* final */ Object channelLock = new Object();

    protected transient volatile Channel channel;

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
        return Objects.toStringHelper(this).
                add("state", state).
                add("error", error).
                add("grantId", grantId).
                add("approved", approved).
                add("node", nodeName).
                add("endpoint", endpoint).
                add("channel", channel).
                add("identity", getIdentity()).toString();
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
        setState(State.Created);
        this.grantId = grantId;
        save();

        taskListener.getLogger().println("Created");
        taskListener.getLogger().println(toString());
    }

    public void setProvisionStartedState(Node node) throws IOException {
        setState(State.Provisioning);
        this.nodeName = node.getNodeName();
        this.node = node;
        save();

        taskListener.getLogger().println("Provisioning");
        taskListener.getLogger().println(toString());
    }

    public void setProvisionCompletedState(Node node, URL endpoint) throws IOException {
        // Potentially may go from the provisioning state to the connectable state
        // Do set set state if >= connectable
        if (this.state == State.Provisioning) {
            setState(State.Provisioned);
            this.nodeName = node.getNodeName();
            this.node = node;
            this.endpoint = endpoint;
            save();
        }

        taskListener.getLogger().println("Provisioned");
        taskListener.getLogger().println(toString());
    }

    public void setProvisionErrorState(Node node, Throwable error) throws IOException {
        setState(State.Provisioning);
        this.error = error;

        taskListener.getLogger().println("Provision Error");
        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Provision Error"));
    }


    public void setConnectableState(RSAPublicKey pk, URL endpoint) throws IOException {
        setState(State.Connectable);
        this.identity = pk.getEncoded();
        this.endpoint = endpoint;
        this.approved = true;
        save();

        taskListener.getLogger().println("Connectable");
        taskListener.getLogger().println(toString());
    }

    public void setConnectedState(Channel channel) throws IOException {
        this.error = null;

        setChannel(channel);

        channel.setProperty(SlaveManager.class.getName(),
                channel.export(SlaveManager.class, new MetaNectarSlaveManager()));

        taskListener.getLogger().println("Connected");
        taskListener.getLogger().println(toString());
    }

    public void setDisconnectState() throws IOException {
        this.channel.close();

        taskListener.getLogger().println("Disconnecting");
        taskListener.getLogger().println(toString());
    }

    public void setTerminateStartedState() throws IOException {
        setState(State.Terminating);
        this.channel.close();
        save();

        taskListener.getLogger().println(toString());
    }

    public void setTerminateCompletedState() throws IOException {
        setState(State.Terminated);
        this.node = null;
        this.nodeName = null;
        this.endpoint = null;
        save();

        taskListener.getLogger().println(toString());
    }

    public void setTerminateErrorState(Throwable error) throws IOException {
        setState(State.Terminating);
        this.error = error;
        save();

        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Terminating Error"));
    }

    private void setState(State state) {
        this.state = state;
        this.error = null;
    }

    // Methods for accessing state

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

            node = MetaNectar.getInstance().getNode(nodeName);
        }

        return node;
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
            return "computer-gray.png";

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


    //

    private void setChannel(Channel channel) throws IOException, IllegalStateException {
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
                return;
            }

            this.channel = channel;
        }

        this.channel.addListener(new Channel.Listener() {
            @Override
            public void onClosed(Channel c, IOException cause) {
                MasterServer.this.channel = null;

                // Orderly shutdown will have null exception
                if (cause != null) {
                    MasterServer.this.setDisconnectStateCallback(cause);
                } else {
                    MasterServer.this.setDisconnectStateCallback();
                }
            }
        });
    }

    private void setDisconnectStateCallback() {
        this.error = null;

        taskListener.getLogger().println("Disconnected");
        taskListener.getLogger().println(toString());
    }

    private void setDisconnectStateCallback(Throwable error) {
        this.error = error;

        taskListener.getLogger().println("Disconnected Error");
        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Disconnected Error"));
    }


    public Map<String,String> getThreadDump() throws IOException, InterruptedException {
        return RemotingDiagnostics.getThreadDump(getChannel());
    }


    //

    public void doDisconnect() throws IOException {
        setDisconnectState();
    }

    public synchronized void doConfigSubmit(StaplerRequest req,
            StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");
        save();
    }

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        HttpResponses.redirectViaContextPath(getUrl()).generateResponse(req,rsp,node);
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
