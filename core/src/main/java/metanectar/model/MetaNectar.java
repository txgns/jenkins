package metanectar.model;

import hudson.PluginManager;
import hudson.Util;
import hudson.model.Failure;
import hudson.model.Hudson;
import hudson.model.ListView;
import hudson.model.View;
import hudson.util.IOUtils;
import hudson.views.JobColumn;
import hudson.views.StatusColumn;
import metanectar.model.views.JenkinsServerColumn;
import org.jvnet.hudson.reactor.ReactorException;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerResponse;

import javax.print.attribute.standard.JobName;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * The root object of MetaNectar.
 *
 * Extends from {@link Hudson} to keep existing code working.
 *
 * @author Paul Sandoz
 * @author Kohsuke Kawaguchi
 */
public class MetaNectar extends Hudson {
    public MetaNectar(File root, ServletContext context) throws IOException, InterruptedException, ReactorException {
        super(root, context);
    }

    public MetaNectar(File root, ServletContext context, PluginManager pluginManager) throws IOException, InterruptedException, ReactorException {
        super(root, context, pluginManager);
    }

    @Override
    public String getDisplayName() {
        return Messages.MetaNectar_DisplayName();
    }

    /**
     * Sets up the initial view state.
     */
    @Override
    protected View createInitialView() {
        try {
            JenkinsServerListView lv = new JenkinsServerListView("All");
            lv.setColumns(Arrays.asList(
                    new StatusColumn(),
                    new JenkinsServerColumn()));
            return lv;

        } catch (IOException e) {
            // view doesn't save itself unless it's connected to the parent, which we don't do in this method.
            // so this never happens
            throw new AssertionError(e);
        }
    }

    /**
     * Registers a new Nectar instance to this MetaNectar.
     */
    public JenkinsServer doAddNectar(@QueryParameter URL url) throws IOException, HttpResponseException {
        checkPermission(ADMINISTER);

        final URLConnection con = url.openConnection();
        if (!(con instanceof HttpURLConnection))
            // otherwise this gives access to local files, etc.
            throw new Failure(url+ " is not a valid HTTP URL");

        final HttpURLConnection hcon = (HttpURLConnection) con;
        hcon.connect();
        if (hcon.getResponseCode()>=400) {
            StringWriter sw = new StringWriter();
            sw.write(url+ " couldn't be retrieved. Status code was "+hcon.getResponseCode()+"\n");
            IOUtils.copy(new InputStreamReader(hcon.getErrorStream()), sw); // TODO: use the right encoding
            throw new Failure(sw.toString(),true);
        }

        if (hcon.getHeaderField("X-Jenkins")==null)
            throw new Failure(url+ " is neither Jenkins nor Nectar --- there's no X-Jenkins header");

        // TODO: use headers to retrieve the public key, instance ID, etc.
        final String mnc = hcon.getHeaderField("X-MetaNectar-Client");

        // add a new server
        final JenkinsServer server = createProject(JenkinsServer.class, Util.getDigestOf(url.toExternalForm()));
        server.setServerUrl(url);

        return server;
    }
}