package metanectar.model;

import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.StatusIcon;
import hudson.model.StockStatusIcon;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.remoting.Channel;
import hudson.slaves.OfflineCause;
import hudson.util.FormValidation;
import hudson.util.RemotingDiagnostics;
import hudson.util.StreamTaskListener;
import hudson.util.io.ReopenableFileOutputStream;
import metanectar.provisioning.MetaNectarSlaveManager;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.*;

/**
 * Representation of remote Jenkins server inside Meta Nectar.
 *
 * TODO should we separate out the connection information in a new class?
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
 */
public class MasterServer extends AbstractItem implements TopLevelItem, HttpResponse {
    /**
     * Points to the top page of the Jenkins.
     */
    protected URL serverUrl;

    /**
     * The encoded image of the public key that indicates the identitiy of this server.
     */
    private volatile byte[] identity;

    /**
     * If the connection to this Jenkins is approved, set to true.
     */
    private volatile boolean approved;

    protected transient volatile OfflineCause offlineCause;

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
        updateOfflineCause();
        log = new ReopenableFileOutputStream(getLogFile());
        taskListener = new StreamTaskListener(log);
        channelLock = new Object();
    }

    public File getLogFile() {
        return new File(getRootDir(),"log.txt");
    }

    /**
     * If the connection to this Jenkins has already been acknowledged, return the public key of that server.
     * Otherwise null.
     */
    public synchronized RSAPublicKey getIdentity() {
        try {
            return (RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(identity));
        } catch (GeneralSecurityException e) {
            LOGGER.log(Level.WARNING,"Failed to load the key", identity);
            identity = null;
            return null;
        }
    }

    /**
     * Acknowledge the given public key as a valid identity of this server
     * (thereby granting future connections from this Jenkins.)
     */
    public void setIdentity(RSAPublicKey pk) throws IOException {
        checkPermission(CONFIGURE);
        identity = pk.getEncoded();
        approved = false;
        save();
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) throws IOException {
        this.approved = approved;
        save();
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

    public StatusIcon getIconColor() {
        String icon = getIcon();
        if (isOffline())  {
            return new StockStatusIcon(icon, Messages._JenkinsServer_Status_Offline());
        } else {
            return new StockStatusIcon(icon, Messages._JenkinsServer_Status_Online());
        }
    }

    public final URL getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(URL url) throws IOException {
        this.serverUrl = url;
        save();
    }

    public String getIcon() {
        if (!isApproved())
            return "computer-gray.png";
        if(isOffline())
            return "computer-x.png";
        else
            return "computer.png";
    }

    public Channel getChannel() {
        return channel;
    }

    @Override
    public String getDisplayName() {
        return serverUrl.toExternalForm();
    }

    public void setChannel(InputStream in, OutputStream out, Channel.Listener listener) throws IOException, InterruptedException {
        if(this.channel!=null)
            throw new IllegalStateException("Already connected");

        Channel channel = new Channel(name, Computer.threadPoolForRemoting, Channel.Mode.NEGOTIATE,
            in,out, taskListener.getLogger());
        if(listener!=null)
            channel.addListener(listener);
        setChannel(channel);
    }

    /**
     * Call this method when a connection is established with this server.
     */
    public void setChannel(Channel channel) throws IOException {
        channel.addListener(new Channel.Listener() {
            @Override
            public void onClosed(Channel c, IOException cause) {
                MasterServer.this.channel = null;
                // Orderly shutdown will have null exception
                if (cause!=null) {
                    offlineCause = new OfflineCause.ChannelTermination(cause);
                     cause.printStackTrace(taskListener.error("Connection terminated"));
                } else {
                    taskListener.getLogger().println("Connection terminated");
                }
            }
        });

        // TODO call some stuff on channel for initializing, logging etc

        offlineCause = null;

        // update the data structure atomically to prevent others from seeing a channel that's not properly initialized yet
        synchronized(channelLock) {
            if(this.channel!=null) {
                // check again. we used to have this entire method in a big sycnhronization block,
                // but Channel constructor blocks for an external process to do the connection
                // if CommandLauncher is used, and that cannot be interrupted because it blocks at InputStream.
                // so if the process hangs, it hangs the thread in a lock, and since Hudson will try to relaunch,
                // we'll end up queuing the lot of threads in a pseudo deadlock.
                // This implementation prevents that by avoiding a lock. HUDSON-1705 is likely a manifestation of this.
                channel.close();
                throw new IllegalStateException("Already connected");
            }
            this.channel = channel;
        }
        onSetChannel();
    }

    private void onSetChannel() {
        channel.setProperty(SlaveManager.class.getName(),
                channel.export(SlaveManager.class, new MetaNectarSlaveManager()));
    }

    public boolean isOnline() {
        return getChannel() != null;
    }

    public boolean isOffline() {
        return getChannel() == null;
    }

    public OfflineCause getOfflineCause() {
        if (offlineCause == null) {
            updateOfflineCause();
        }
        return offlineCause;
    }

    public void updateOfflineCause() {
        offlineCause = pollOfflineCause();
    }

    public OfflineCause pollOfflineCause() {
        if (isOnline())
            return null;

        // TODO this should not occur
        if (serverUrl == null)
            return null;

        // TODO this should probably be made in another thread so as not to block

        // Ping the URL to determine if server is online
        try {
            HttpURLConnection c = (HttpURLConnection)serverUrl.openConnection();
            c.setRequestMethod("HEAD");
            c.getResponseCode();
        } catch (IOException ex) {
            return OfflineCause.create(Messages._JenkinsServer_ServerOffline());
        }

        // If server is online wait for connection from server
        return OfflineCause.create(Messages._JenkinsServer_EstablishConnection());
    }

    public HttpResponse doPollOffline() {
        updateOfflineCause();
        return HttpResponses.redirectToDot();
    }

    public HttpResponse doDisconnect() throws IOException {
        channel.close();
        updateOfflineCause();
        return HttpResponses.redirectToDot();
    }

    public Map<String,String> getThreadDump() throws IOException, InterruptedException {
        return RemotingDiagnostics.getThreadDump(getChannel());
    }

    public synchronized void doConfigSubmit(StaplerRequest req,
            StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");

        serverUrl = new URL(req.getParameter("_.serverUrl"));

        try {
            JSONObject json = req.getSubmittedForm();

            save();

            String newName = req.getParameter("name");
            if (newName != null && !newName.equals(name)) {
                // check this error early to avoid HTTP response splitting.
                Hudson.checkGoodName(newName);
                rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8"));
            } else {
                rsp.sendRedirect(".");
            }
        } catch (JSONException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("Failed to parse form data. Please report this problem as a bug");
            pw.println("JSON=" + req.getSubmittedForm());
            pw.println();
            e.printStackTrace(pw);

            rsp.setStatus(SC_BAD_REQUEST);
            sendError(sw.toString(), req, rsp, true);
        }
    }

    /**
     * When used as an HTTP response, issue a redirect to this server.
     */
    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        HttpResponses.redirectViaContextPath(getUrl()).generateResponse(req,rsp,node);
    }

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return "Master server";
        }

        public FormValidation doCheckServerUrl(@QueryParameter String value) throws IOException, ServletException {
            if(value.length()==0)
                return FormValidation.error("Please set a server URL");

            try {
                URL u = new URL(value);
            } catch (Exception e) {
                return FormValidation.error("Not a URL");
            }

            return FormValidation.ok();
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new MasterServer(parent,name);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MasterServer.class.getName());
}
