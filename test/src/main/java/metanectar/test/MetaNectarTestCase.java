/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Erik Ramfelt, Koichi Fujikawa, Red Hat, Inc., Seiji Sogabe,
 * Stephen Connolly, Tom Huybrechts, Yahoo! Inc., Alan Harder, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package metanectar.test;

import hudson.model.Hudson;
import hudson.tasks.Mailer;
import metanectar.Config;
import metanectar.model.MetaNectar;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestPluginManager;
import org.jvnet.hudson.test.recipes.Recipe;

import javax.servlet.ServletContext;
import java.io.File;

/**
 * @author Paul Sandoz
 */
public class MetaNectarTestCase extends HudsonTestCase {

    protected MetaNectarTestCase(String name) {
        super(name);
    }

    protected MetaNectarTestCase() {
    }

    private Config config;

    protected void setConfig(Config config) {
        this.config = config;
    }

    public MetaNectar metaNectar;

    protected Hudson newHudson() throws Exception {
        final File home = homeLoader.allocate();
        for (Recipe.Runner r : recipes)
            r.decorateHome(this, home);

        if (config == null) {
            config = new Config();
        }

        final ServletContext context = createWebServer();

        // Set test configuration defaults if not already set
        setDefault("metaNectar.endpoint", getURL().toExternalForm());
        setDefault("metaNectar.master.provisioning.archive", home.getAbsolutePath());

        metaNectar = new MetaNectar(home, context,
                useLocalPluginManager ? null : TestPluginManager.INSTANCE,
                config);
        return metaNectar;
    }

    private void setDefault(String name, String value) {
        if (!config.getProperties().containsKey(name)) {
            config.getProperties().setProperty(name, value);
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Reset the endpoint URL
        // For some reason the Hudson.setUp resets the value to null !!!
        Mailer.descriptor().setHudsonUrl(config.getEndpoint().toExternalForm());
    }

}
