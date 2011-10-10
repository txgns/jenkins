package metanectar.model;

import com.cloudbees.commons.metanectar.context.ItemNodeContext;
import com.cloudbees.commons.metanectar.context.NodeContainer;
import com.cloudbees.commons.metanectar.context.NodeContext;
import com.cloudbees.commons.metanectar.context.NodeContextContributor;
import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.PeriodicWork;
import hudson.model.StatusIcon;
import hudson.model.StockStatusIcon;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.util.DescribableList;
import hudson.util.DirScanner;
import hudson.util.StreamTaskListener;
import hudson.util.io.ArchiverFactory;
import hudson.util.io.ReopenableFileOutputStream;
import metanectar.provisioning.IdentifierFinder;
import metanectar.provisioning.ScopedSlaveManager;
import net.jcip.annotations.GuardedBy;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A master capable of connecting to MetaNectar.
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
 */
public abstract class ConnectedMaster extends AbstractItem implements TopLevelItem {

    /**
     * A unique number that is always less than the total number of masters created.
     */
    protected int id;

    /**
     * The name encoded for safe use within URL path segments.
     */
    protected String encodedName;

    /**
     * A unique name comprising of the id and the encoded name.
     * This is suitable for use as a master home directory name.
     */
    protected String idName;

    /**
     * The time stamp when the state was modified.
     *
     * @see {@link java.util.Date#getTime()}.
     */
    protected volatile long timeStamp;

    /**
     * Error associated with a particular state.
     */
    protected transient volatile Throwable error;

    /**
     * The grant ID for the master to validate when initially connecting.
     */
    protected String grantId;

    /**
     * If the connection to this master is approved, set to true.
     */
    protected volatile boolean approved;

    /**
     * The local home directory of the master.
     */
    protected volatile String localHome;

    /**
     * The local URL to the master.
     */
    protected volatile URL localEndpoint;

    private /*almost final*/ transient Object identityLock;

    /**
     * The encoded image of the public key that indicates the identity of the master.
     */
    @GuardedBy("identityLock")
    private byte[] identity;

    /**
     * The encoded image of the public key that indicates the identity of the master.
     */
    @GuardedBy("identityLock")
    private transient RSAPublicKey identityPublicKey;

    // connected state

    protected transient volatile Channel channel;

    protected transient SlaveManager slaveManager;

    // logging state

    protected transient ReopenableFileOutputStream log;

    protected transient TaskListener taskListener;

    // property state

    protected volatile DescribableList<ConnectedMasterProperty,ConnectedMasterPropertyDescriptor> properties =
            new PropertyList(this);

    @GuardedBy("this")
    private transient NodeContext nodeContext;

    @GuardedBy("this")
    private transient byte[] remoteNodeContextDigest;

    protected ConnectedMaster(ItemGroup parent, String name) {
        super(parent, name);

    }

    public Objects.ToStringHelper toStringHelper() {
        final RSAPublicKey key = getIdentityPublicKey();
        return Objects.toStringHelper(this).
                add("id", id).
                add("name", name).
                add("encodedName", encodedName).
                add("idName", idName).
                add("timeStamp", timeStamp).
                add("error", error).
                add("grantId", grantId).
                add("approved", approved).
                add("localHome", localHome).
                add("localEndpoint", localEndpoint).
                add("channel", channel).
                add("identity", (key == null) ? null : key.getFormat() + ", " + key.getAlgorithm());
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

        try {
            this.id = ConnectedMaster.CONNECTED_MASTER_IDENTIFIER_FINDER.getUnusedIdentifier(MetaNectar.getInstance().getConnectedMasters());
            this.encodedName = createEncodedName(name);
            this.idName = createIdName(id, encodedName);

            setCreatedState();
            save();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void init() {
        log = new ReopenableFileOutputStream(getLogFile());
        taskListener = new StreamTaskListener(log);
        identityLock = new Object();

        if (properties == null) {
            properties = new PropertyList(this);
        } else {
            properties.setOwner(this);
        }
    }

    // Properties

    public DescribableList<ConnectedMasterProperty,ConnectedMasterPropertyDescriptor> getProperties() {
        return properties;
    }

    public List<hudson.model.Action> getPropertyActions() {
        ArrayList<hudson.model.Action> result = new ArrayList<hudson.model.Action>();
        for (ConnectedMasterProperty prop: properties) {
            result.addAll(prop.getConnectedMasterActions(this));
        }
        return result;
    }

    @Override
    public List<Action> getActions() {
        List<Action> result = new ArrayList<Action>(super.getActions());
        result.addAll(getPropertyActions());
        return Collections.unmodifiableList(result);
    }

    @Override
    public void addAction(Action a) {
        if(a==null) throw new IllegalArgumentException();
        super.getActions().add(a);
    }

    // Logging

    private File getLogFile() {
        return new File(getRootDir(),"log.txt");
    }

    public String getLog() throws IOException {
        return Util.loadFile(getLogFile());
    }

    public AnnotatedLargeText<TopLevelItem> getLogText() {
        return new AnnotatedLargeText<TopLevelItem>(getLogFile(), Charset.defaultCharset(), false, this);
    }

    public void doProgressiveLog( StaplerRequest req, StaplerResponse rsp) throws IOException {
        getLogText().doProgressText(req,rsp);
    }

    //

    /**
     * No nested jobs
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


    //

    protected abstract void setCreatedState() throws IOException;

    public abstract boolean isApprovable();

    public abstract void setApprovedState(RSAPublicKey pk, URL endpoint) throws IOException;

    public abstract void setReapprovedState() throws IOException;

    public abstract void setApprovalErrorState(Throwable error) throws IOException;


    //

    public void setConnectedState(Channel channel) throws IOException {
        TaskListener taskListener;
        synchronized (this) {
            if (!setChannel(channel)) {
                return;
            }

            this.error = null;
            taskListener = this.taskListener;

            slaveManager = new ScopedSlaveManager(getParent());
            try {
                NodeContainer.set(channel, SlaveManager.class.getName(),
                        (Serializable)channel.export(SlaveManager.class, slaveManager));
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Interrupted while trying to pass SlaveManager. Master will be unable to lease slaves.", e);
                taskListener.getLogger().println("Interrupted while trying to pass SlaveManager. Master will be unable to lease slaves.");
                e.printStackTrace(taskListener.getLogger());
            } catch (AssertionError e) {
                LogRecord lr = new LogRecord(Level.SEVERE, "Could not pass SlaveManager to {0} and it will be unable to lease slaves.");
                lr.setThrown(e);
                lr.setParameters(new Object[]{this.getDisplayName()});
                LOGGER.log(lr);
                taskListener.getLogger().println("Could not pass SlaveManager, leasing of slaves disabled");
                e.printStackTrace(taskListener.getLogger());
            } catch (IOException e) {
                if (e.getCause() instanceof AssertionError) {
                    LogRecord lr = new LogRecord(Level.SEVERE,
                            "Could not pass SlaveManager to {0} and it will be unable to lease slaves.");
                    lr.setThrown(e);
                    lr.setParameters(new Object[]{this.getDisplayName()});
                    LOGGER.log(lr);
                    taskListener.getLogger().println("Could not pass SlaveManager, leasing of slaves disabled");
                    e.printStackTrace(taskListener.getLogger());
                } else {
                    throw e;
                }
            }
        }
        ConnectedMasterListener.fireOnConnected(this);
        ConnectedMasterProperty.fireOnConnected(this);
        updateNodeContext();

        taskListener.getLogger().println("Connected");
        taskListener.getLogger().println(toString());
    }

    @SuppressWarnings("unchecked")
    protected NodeContext createNodeContext() {
        final ItemNodeContext result = new ItemNodeContext(MetaNectar.getInstance().getSecurityRealm(),
                MetaNectar.getInstance().getAuthorizationStrategy(),
                MetaNectar.getInstance().getRootUrl(), this);
        for (NodeContextContributor c : Hudson.getInstance().getExtensionList(NodeContextContributor.class)) {
            if (c.canContribute(this)) {
                try {
                    c.contribute(this, result);
                } catch (Throwable t) {
                    LogRecord r = new LogRecord(Level.WARNING, "Uncaught exception from {0} on {1}");
                    r.setThrown(t);
                    r.setParameters(new Object[]{c, this});
                    LOGGER.log(r);
                }
            }
        }
        return result;
    }

    @NonNull
    public NodeContext getNodeContext() {
        while (true) {
            NodeContext nodeContext;
            synchronized (this) {
                nodeContext = this.nodeContext;
            }
            if (nodeContext == null) {
                NodeContext newNodeContext = createNodeContext();
                synchronized (this) {
                    if (nodeContext == this.nodeContext) {
                        this.nodeContext = newNodeContext;
                        remoteNodeContextDigest = null;
                        return this.nodeContext;
                    }
                }
            } else {
                return nodeContext;
            }
        }
    }

    protected void updateNodeContext() {
        final Channel channel = this.channel;
        if (channel == null) {
            LOGGER.log(Level.FINE, "Cannot update context for {0} as no connection available", this);
            synchronized (this) {
                remoteNodeContextDigest = null;
            }
            return;
        }
        final NodeContext nodeContext = getNodeContext();

        for (NodeContextContributor c : Hudson.getInstance().getExtensionList(NodeContextContributor.class)) {
            if (c.canContribute(this)) {
                try {
                    c.update(this, nodeContext);
                } catch (Throwable t) {
                    LogRecord r = new LogRecord(Level.WARNING, "Uncaught exception from {0} on {1}");
                    r.setThrown(t);
                    r.setParameters(new Object[]{c, this});
                    LOGGER.log(r);
                }
            }
        }

        final byte[] currentDigest = nodeContext.digest();
        synchronized (this) {
            if (!Arrays.equals(currentDigest, remoteNodeContextDigest)) {
                LOGGER.log(Level.INFO, "Updating context for {0} as it was out of date", this);

                try {
                    NodeContainer.set(channel, NodeContext.class.getName(), nodeContext);
                    remoteNodeContextDigest = currentDigest;
                } catch (IOException e) {
                    LogRecord lr = new LogRecord(Level.WARNING, "Could not update context for {0}");
                    lr.setParameters(new Object[]{this});
                    lr.setThrown(e);
                    LOGGER.log(lr);
                } catch (InterruptedException e) {
                    LogRecord lr = new LogRecord(Level.WARNING, "Interrupted while trying to update context for {0}");
                    lr.setParameters(new Object[]{this});
                    lr.setThrown(e);
                    LOGGER.log(lr);
                }
            } else {
                LOGGER.log(Level.FINE, "Context for {0} has not changed since last updated", this);
            }
        }
    }

    // State querying

    /**
     * Query the master state using a synchronized block.
     *
     * @param f the function to query the master state.
     */
    public synchronized <T extends ConnectedMaster> void query(Function<T, Void> f) {
        f.apply((T)this);
    }

    // Methods for accessing state

    public int getId() {
        return id;
    }

    public String getEncodedName() {
        return encodedName;
    }

    public String getIdName() {
        return idName;
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

    public URL getEndpoint() {
        return localEndpoint;
    }

    public String getLocalHome() {
        return localHome;
    }

    public URL getLocalEndpoint() {
        return localEndpoint;
    }

    public byte[] getIdentity() {
        return identity;
    }

    public void setIdentity(byte[] identity) {
        synchronized (identityLock) {
            this.identity = identity == null ? null : identity.clone();
            this.identityPublicKey = null;
        }
    }

    public RSAPublicKey getIdentityPublicKey() {
        synchronized (identityLock) {
            if (identity == null)
                return null;

            if (identityPublicKey != null)
                return identityPublicKey;

            try {
                identityPublicKey = (RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(identity));
            } catch (GeneralSecurityException e) {
                LOGGER.log(Level.SEVERE, "Failed to load the key", identity);
                identityPublicKey = null;
            }

            return identityPublicKey;
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


    // Channel-specific methods that can only be performed when online

    /**
     * Clone the master home directory to a local tar.gz file.
     * <p>
     * This operation is synchronous.</p>
     * <p>
     * Any instance specific files, such as those related to identity, will be removed.</p>
     */
    public void cloneHomeDir(File archive, ArchiverFactory af) throws IOException, InterruptedException {
        if (isOffline()) {
            throw new IllegalStateException(String.format("Master %s is not online", getName()));
        }

        FilePath fp = getHomeDir();

        // Note that the glob scanner will not include the top-level directory in the archive
        fp.archive(af,
                new BufferedOutputStream(new FileOutputStream(archive)),
                new DirScanner.Glob("", "identity.key, secret.key, jobs/**/builds, jobs/**/workspace"));
    }

    /**
     * Get the master home directory.
     * <p>
     * This operation is synchronous.
     * </p>
     */
    public FilePath getHomeDir() throws IOException, InterruptedException {
        if (isOffline()) {
            throw new IllegalStateException("Not online");
        }

        return channel.call(new HomeDirCallable());
    }

    private static class HomeDirCallable implements Callable<FilePath, RuntimeException> {
        public FilePath call() throws RuntimeException {
            return Hudson.getInstance().getRootPath();
        }
    }

    protected boolean setChannel(Channel channel) throws IOException, IllegalStateException {
        if (this.channel != null) {
            // TODO we need to check if the existing channel is still alive or not,
            // if not use the new channel, otherwise close the channel

            channel.close();
            LOGGER.warning("Already connected");
            return false;
        }

        this.channel = channel;

        this.channel.addListener(new Channel.Listener() {
            @Override
            public void onClosed(Channel c, IOException cause) {
                ConnectedMaster.this.channel = null;
                ConnectedMaster.this.slaveManager = null;
                updateNodeContext();

                // Orderly shutdown will have null exception
                try {
                    if (cause != null) {
                        ConnectedMaster.this.setDisconnectStateCallback(cause);
                    } else {
                        ConnectedMaster.this.setDisconnectStateCallback();
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        return true;
    }

    protected void setDisconnectStateCallback() throws IOException {
        ConnectedMasterListener.fireOnDisconnected(this);
        ConnectedMasterProperty.fireOnDisconnected(this);

        taskListener.getLogger().println("Disconnected");
        taskListener.getLogger().println(toString());
    }

    protected void setDisconnectStateCallback(Throwable error) throws IOException {
        this.error = error;

        ConnectedMasterListener.fireOnDisconnected(this);
        ConnectedMasterProperty.fireOnDisconnected(this);

        taskListener.getLogger().println("Disconnected Error");
        taskListener.getLogger().println(toString());
        error.printStackTrace(taskListener.error("Disconnected Error"));
    }

    // Test stuff

    public HttpResponse doDisconnect() throws Exception {
        checkPermission(Hudson.ADMINISTER);
        requirePOST();

        this.channel.close();

        taskListener.getLogger().println("Disconnecting");
        taskListener.getLogger().println(toString());
        return HttpResponses.redirectToDot();
    }

    //

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

    //

    /**
     * Create a unique master template file for writing a template of a master home
     * directory.
     *
     */
    public static TemplateFile createMasterTemplateFile(String dir, String suffix) throws IOException {
        return new TemplateFile(createMasterArchiveFile(dir, "template-", suffix), suffix);
    }

    /**
     * Create a unique master snapshot file for writing a snapshot of a master home
     * directory.
     *
     */
    public static File createMasterSnapshotFile(String dir, String suffix) throws IOException {
        return createMasterArchiveFile(dir, "snapshot-", suffix);
    }

    private static File createMasterArchiveFile(String dir, String prefix, String suffix) throws IOException {
        File f = null;
        do {
            f = new File(dir, prefix + UUID.randomUUID().toString() + suffix);
        } while (!f.createNewFile());

        return f;
    }

    /**
     * Create the encoded name.
     */
    public static String createEncodedName(String name) {
        return Util.rawEncode(name);
    }

    /**
     * Create the ID name given a unique ID of a master and it's name.
     */
    public static String createIdName(int id, String name) {
        return Integer.toString(id) + "-" + name;
    }

    /**
     * @return a created grant ID.
     */
    public static String createGrant() {
        return UUID.randomUUID().toString();
    }

    public static class PropertyList extends DescribableList<ConnectedMasterProperty,ConnectedMasterPropertyDescriptor> {
        private PropertyList(ConnectedMaster owner) {
            super(owner);
        }

        public PropertyList() {// needed for XStream deserialization
        }

        public ConnectedMaster getOwner() {
            return (ConnectedMaster) owner;
        }

        @Override
        protected void onModified() throws IOException {
            if (owner instanceof ConnectedMaster) {
                for (ConnectedMasterProperty p : this) {
                    p.setOwner(getOwner());
                }
            }
        }
    }

    private static IdentifierFinder<ConnectedMaster> CONNECTED_MASTER_IDENTIFIER_FINDER = new IdentifierFinder<ConnectedMaster>() {
        public int getId(ConnectedMaster ms) {
            return ms.getId();
        }
    };

    /**
     * Updates the node contexts on a periodic basis.
     */
    @Extension
    public static class UpdateNodeContexts extends PeriodicWork {

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(30);
        }

        @Override
        protected void doRun() throws Exception {
            for (ConnectedMaster master : MetaNectar.getInstance().getAllItems(ConnectedMaster.class)) {
                master.updateNodeContext();
            }
        }
    }


    private static final Logger LOGGER = Logger.getLogger(ConnectedMaster.class.getName());
}
