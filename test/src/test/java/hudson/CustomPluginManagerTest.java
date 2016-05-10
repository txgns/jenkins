/*
 * The MIT License
 * 
 * Copyright (c) 2016 CloudBees, Inc.
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
package hudson;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.junit.AssumptionViolatedException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.ThreadPoolImpl;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.jvnet.hudson.test.recipes.WithPluginManager;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextListener;
import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the use of a custom plugin manager in custom wars.
 */
public class CustomPluginManagerTest {

    // TODO: to be greatly simplified when JENKINS-34701 is available.
    @Rule public final JenkinsRule r = new JenkinsRule() {
        private File exploded() {
            File d = new File(".").getAbsoluteFile();
            for( ; d!=null; d=d.getParentFile()) {
                if(new File(d,".jenkins").exists()) {
                    File dir = new File(d,"war/target/jenkins");
                    if(dir.exists()) {
                        System.out.println("Using jenkins.war resources from "+dir);
                        return dir;
                    }
                }
            }
            // Temporal solution until JENKINS-34701 is available.
            throw new AssumptionViolatedException("No jenkins.war resources available");
        }

        protected ServletContext createWebServer() throws Exception {
            final File exploded = exploded();

            server = new Server(new ThreadPoolImpl(new ThreadPoolExecutor(10, 10, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("Jetty Thread Pool");
                    return t;
                }
            })));

            WebAppContext context = new WebAppContext(exploded.getPath(), contextPath);
            context.setClassLoader(getClass().getClassLoader());
            context.setConfigurations(new Configuration[]{new WebXmlConfiguration()});
            context.addBean(new NoListenerConfiguration(context));
            server.setHandler(context);
            context.setMimeTypes(MIME_TYPES);
            context.getSecurityHandler().setLoginService(configureUserRealm());
            context.setResourceBase(exploded.getPath());

            context.setInitParameter(PluginManager.class.getName() + ".className", CustomPluginManager.class.getClass().getName());

            ServerConnector connector = new ServerConnector(server);
            HttpConfiguration config = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();
            // use a bigger buffer as Stapler traces can get pretty large on deeply nested URL
            config.setRequestHeaderSize(12 * 1024);
            connector.setHost("localhost");
            if (System.getProperty("port")!=null)
                connector.setPort(Integer.parseInt(System.getProperty("port")));

            server.addConnector(connector);
            server.start();

            localPort = connector.getLocalPort();
            Logger.getLogger(HudsonTestCase.class.getName()).log(Level.INFO, "Running on {0}", getURL());

            return context.getServletContext();
        }

    };

    /**
     * Kills off {@link ServletContextListener}s loaded from web.xml.
     *
     * <p>
     * This is so that the harness can create the {@link jenkins.model.Jenkins} object.
     * with the home directory of our choice.
     *
     * @author Kohsuke Kawaguchi
     */
    private static final class NoListenerConfiguration extends AbstractLifeCycle {
        private final WebAppContext context;

        NoListenerConfiguration(WebAppContext context) {
            this.context = context;
        }

        @Override
        protected void doStart() throws Exception {
            context.setEventListeners(null);
        }
    }

    @Issue("JENKINS-34681")
    @WithPlugin("tasks.jpi")
    @WithPluginManager(CustomPluginManager.class)
    @Test public void customPluginManager() {
        assertTrue("Correct plugin manager installed", r.getPluginManager() instanceof CustomPluginManager);
        assertNotNull("Plugin tasks installed", r.jenkins.getPlugin("tasks"));
    }

    public static class CustomPluginManager extends LocalPluginManager {
        public CustomPluginManager(File home) {
            super(null, home);
        }
    }


}
