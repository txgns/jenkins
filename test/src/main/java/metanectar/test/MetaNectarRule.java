package metanectar.test;

import hudson.model.Hudson;
import hudson.tasks.Mailer;
import metanectar.Config;
import metanectar.model.MetaNectar;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;

import javax.servlet.ServletContext;
import java.io.File;

/**
 * A rule to help running meta-nectar test cases.
 * @author Stephen Connolly
 */
public class MetaNectarRule extends JenkinsRule {

    public MetaNectar metaNectar;

    private Config config;

    protected void setConfig(Config config) {
        this.config = config;
    }

    public MetaNectarRule with(Config config) {
        setConfig(config);
        return this;
    }

    protected Hudson newHudson() throws Exception {
        ServletContext webServer = createWebServer();
        File home = tempFolder.newFolder("jenkins-home-" + testDescription.getDisplayName());
        for (JenkinsRecipe.Runner r : recipes) {
            r.decorateHome(this, home);
        }

        if (config == null) {
            config = new Config();
        }

        // Set test configuration defaults if not already set
        setDefault("metaNectar.endpoint", getURL().toExternalForm());
        setDefault("metaNectar.master.provisioning.archive", home.getAbsolutePath());

        metaNectar = new MetaNectar(home, webServer,
                getPluginManager(),
                config);
        return metaNectar;
    }

    private void setDefault(String name, String value) {
        if (!config.getProperties().containsKey(name)) {
            config.getProperties().setProperty(name, value);
        }
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        // Reset the endpoint URL
        // For some reason the Hudson.setUp resets the value to null !!!
        Mailer.descriptor().setHudsonUrl(config.getEndpoint().toExternalForm());
    }
}
