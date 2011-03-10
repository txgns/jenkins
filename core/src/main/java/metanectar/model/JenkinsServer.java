package metanectar.model;

import hudson.Extension;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.tasks.LogRotator;
import hudson.util.FormValidation;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Representation of remote Jenkins server inside Meta Nectar.
 *
 * @author Kohsuke Kawaguchi
 */
public class JenkinsServer extends AbstractItem implements TopLevelItem {

    protected String serverUrl;

    protected JenkinsServer(ItemGroup parent, String name) {
        super(parent, name);
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

    public final String getServerUrl() {
        return serverUrl;
    }

    public String getIcon() {
        if(isOffline())
            return "computer-x.png";
        else
            return "computer.png";
    }

    public boolean isOffline() {
        return getChannel()==null;
    }

    public VirtualChannel getChannel() {
        return null;
    }

    public synchronized void doConfigSubmit(StaplerRequest req,
            StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");

        serverUrl = req.getParameter("_.serverUrl");

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
