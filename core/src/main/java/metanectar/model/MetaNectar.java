package metanectar.model;

import hudson.PluginManager;
import hudson.model.Hudson;
import hudson.model.ListView;
import hudson.model.View;
import hudson.views.JobColumn;
import hudson.views.StatusColumn;
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
        ListView lv = new ListView("All");
        lv.setColumns(Arrays.asList(
                new StatusColumn(),
                new JobColumn()
        ));
        return lv;
    }
}