/*
 * The MIT License
 *
 * Copyright (c) 2010, Kohsuke Kawaguchi
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

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import javax.servlet.ServletContext;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

/**
 * Default implementation of {@link PluginManager}.
 *
 * <p>
 * <b>Extending Local Plugin Managers</b>. The default plugin manager in {@code Jenkins} can be replaced by defining a
 * System Property (<code>hudson.LocalPluginManager.className</code>). See {@link #create(Jenkins)}.
 * This className should be available on early startup, so it cannot come only from a library
 * (e.g. Jenkins module or Extra library dependency in the WAR file project).
 * Plugins cannot be used for such purpose.
 * In order to be correctly instantiated, the class definition must a constructor with a parameter of type {@link Jenkins}.
 * If the class does not comply with the requirements, a fallback to the default LocalPluginManager will be performed.
 *
 * @author Kohsuke Kawaguchi
 */
public class LocalPluginManager extends PluginManager {
    /** Custom plugin manager system property or context param. */
    public static final String CUSTOM_PLUGIN_MANAGER = PluginManager.class.getName() + ".className";

    /**
     * Creates the default {@link PluginManager} to use.
     * A default implementation is created after the Jenkins object, but when it is not fully initialized.
     * @param jenkins Jenkins Instance.
     * @return Plugin manager to use.
     */
    public static @NonNull LocalPluginManager create(Jenkins jenkins) {
        String pmClassName = System.getProperty(CUSTOM_PLUGIN_MANAGER);
        if (StringUtils.isBlank(pmClassName) && jenkins.servletContext != null) {
            pmClassName = jenkins.servletContext.getInitParameter(CUSTOM_PLUGIN_MANAGER);
        }
        if (!StringUtils.isBlank(pmClassName)) {
            LOGGER.log(FINE, String.format("Use of custom plugin manager [%s] requested.", pmClassName));
            try {
                final Class<? extends LocalPluginManager> klass = Class.forName(pmClassName).asSubclass(LocalPluginManager.class);
                final Constructor<? extends LocalPluginManager> constructor = klass.getConstructor(Jenkins.class);
                return constructor.newInstance(jenkins);
            } catch(NullPointerException e) {
                // Class.forName and Class.getConstructor are supposed to never return null though a broken ClassLoader
                // could break the contract. Just in case we introduce this specific catch to avoid polluting the logs with NPEs.
                LOGGER.log(WARNING, String.format("Unable to instantiate custom plugin manager [%s]. Using default.", pmClassName));
            } catch(ClassCastException e) {
                LOGGER.log(WARNING, String.format("Provided class [%s] does not extend LocalPluginManager. Using default.", pmClassName));
            } catch(NoSuchMethodException e) {
                LOGGER.log(WARNING, String.format("Provided custom plugin manager [%s] does not provided (Jenkins) constructor. Using default.", pmClassName), e);
            } catch(Exception e) {
                LOGGER.log(WARNING, String.format("Unable to instantiate custom plugin manager [%s]. Using default.", pmClassName), e);
            }
        }
        return new LocalPluginManager(jenkins);
    }

    /**
     * Creates a new LocalPluginManager
     * @param context Servlet context. Provided for compatibility as {@code Jenkins.getInstance().servletContext} should be used.
     * @param rootDir Jenkins home directory.
     */
    public LocalPluginManager(ServletContext context, @NonNull File rootDir) {
        super(context, new File(rootDir,"plugins"));
    }

    /**
     * Creates a new LocalPluginManager
     * @param jenkins Jenkins instance that will use the plugin manager.
     */
    public LocalPluginManager(@NonNull Jenkins jenkins) {
        this(jenkins.servletContext, jenkins.getRootDir());
    }

    /**
     * Creates a new LocalPluginManager
     * @param rootDir Jenkins home directory.
     */
    public LocalPluginManager(@NonNull File rootDir) {
        this(null, rootDir);
    }

    /**
     * If the war file has any "/WEB-INF/plugins/*.jpi", extract them into the plugin directory.
     *
     * @return
     *      File names of the bundled plugins. Like {"ssh-slaves.jpi","subvesrion.jpi"}
     */
    @Override
    protected Collection<String> loadBundledPlugins() {
        // this is used in tests, when we want to override the default bundled plugins with .jpl (or .hpl) versions
        if (System.getProperty("hudson.bundled.plugins") != null) {
            return Collections.emptySet();
        }

        try {
            return loadPluginsFromWar("/WEB-INF/plugins");
        } finally {
            loadDetachedPlugins();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(LocalPluginManager.class.getName());
}
