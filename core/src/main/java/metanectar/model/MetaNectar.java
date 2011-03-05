package metanectar.model;

import hudson.PluginManager;
import hudson.model.Hudson;
import org.jvnet.hudson.reactor.ReactorException;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;

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

}