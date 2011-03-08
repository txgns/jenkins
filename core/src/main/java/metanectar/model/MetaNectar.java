package metanectar.model;

import hudson.PluginManager;
import hudson.model.Hudson;
import hudson.model.ListView;
import hudson.model.View;
import hudson.views.JobColumn;
import hudson.views.StatusColumn;
import metanectar.model.views.JenkinsServerColumn;
import org.jvnet.hudson.reactor.ReactorException;

import javax.print.attribute.standard.JobName;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Paul Sandoz
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
        return "MetaNectar";
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
                    new JenkinsServerColumn(),
                    new JobColumn()
            ));
            return lv;
        } catch (IOException e) {
            // view doesn't save itself unless it's connected to the parent, which we don't do in this method.
            // so this never happens
            throw new AssertionError(e);
        }
    }
}