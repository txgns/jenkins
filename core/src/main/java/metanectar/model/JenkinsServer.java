package metanectar.model;

import hudson.Extension;
import hudson.model.*;
import hudson.remoting.Channel;
import hudson.slaves.OfflineCause;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Representation of remote Jenkins server inside Meta Nectar.
 *
 * TODO should we separate out the connection information in a new class?
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
 */
public class JenkinsServer extends AbstractItem implements TopLevelItem {

    protected URL serverUrl;

    protected transient volatile OfflineCause offlineCause;

    protected transient final Object channelLock = new Object();

    protected transient volatile Channel channel;

    protected JenkinsServer(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name)
            throws IOException {
        super.onLoad(parent, name);
        offlineCause = getOfflineCause(serverUrl);
        System.out.println("----------------- onLoad");
    }

    @Override
    public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        offlineCause = getOfflineCause(serverUrl);
        System.out.println("----------------- onCreatedFromScratch");
    }

    protected View createInitialView() {
        return new JenkinsServerAllView(hudson.model.Messages.Hudson_ViewName());
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

    public String getIcon() {
        if(isOffline())
            return "computer-x.png";
        else
            return "computer.png";
    }

    public Channel getChannel() {
        return channel;
    }

    // Copied from SlaveComputer
    public void setChannel(InputStream in, OutputStream out, TaskListener taskListener, Channel.Listener listener) throws IOException, InterruptedException {
        setChannel(in,out,taskListener.getLogger(),listener);
    }

    // Copied from SlaveComputer
    // TODO consider refactoring this functionality into separate utility class
    /**
     * Creates a {@link Channel} from the given stream and sets that to this server.
     *
     * @param in
     *      Stream connected to the remote server. It's the caller's responsibility to do
     *      buffering on this stream, if that's necessary.
     * @param out
     *      Stream connected to the remote server. It's the caller's responsibility to do
     *      buffering on this stream, if that's necessary.
     * @param launchLog
     *      If non-null, receive the portion of data in <tt>is</tt> before
     *      the data goes into the "binary mode". This is useful
     *      when the established communication channel might include some data that might
     *      be useful for debugging/trouble-shooting.
     * @param listener
     *      Gets a notification when the channel closes, to perform clean up. Can be null.
     *      By the time this method is called, the cause of the termination is reported to the user,
     *      so the implementation of the listener doesn't need to do that again.
     */
    public void setChannel(InputStream in, OutputStream out, OutputStream launchLog, Channel.Listener listener) throws IOException, InterruptedException {
        if(this.channel!=null)
            throw new IllegalStateException("Already connected");

        final TaskListener taskListener = new StreamTaskListener(launchLog);
        PrintStream log = taskListener.getLogger();

        Channel channel = new Channel(name, Computer.threadPoolForRemoting, Channel.Mode.NEGOTIATE,
            in,out, launchLog);
        channel.addListener(new Channel.Listener() {
            @Override
            public void onClosed(Channel c, IOException cause) {
                JenkinsServer.this.channel = null;
                // Orderly shutdown will have null exception
                if (cause!=null) {
                    offlineCause = new OfflineCause.ChannelTermination(cause);
                     cause.printStackTrace(taskListener.error("Connection terminated"));
                } else {
                    taskListener.getLogger().println("Connection terminated");
                }
            }
        });
        if(listener!=null)
            channel.addListener(listener);

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

        // TODO notifications
//        for (ComputerListener cl : ComputerListener.all())
//            cl.onOnline(this,taskListener);
        log.println("Server successfully connected and online");
    }

    public OfflineCause getOfflineCause() {
        return offlineCause;
    }

    public boolean isOffline() {
        return getChannel()==null;
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

    private static OfflineCause getOfflineCause(URL url) {
        if (url == null)
            return null;

        // TODO this should probably be made in another thread so as not to block

        // Ping the URL to determine if server is online
        try {
            HttpURLConnection c = (HttpURLConnection)url.openConnection();
            c.setRequestMethod("HEAD");
            c.getResponseCode();
        } catch (IOException ex) {
            return OfflineCause.create(Messages._JenkinsServer_ServerOffline());
        }

        // If server is online wait for connection from server
        return OfflineCause.create(Messages._JenkinsServer_EstablishConnection());
    }

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return "Jenkins server";
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
            return new JenkinsServer(parent,name);
        }
    }
}
