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

import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithPlugin;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the use of a custom plugin manager in custom wars.
 */
public class CustomPluginManagerTest {
    @Rule public final JenkinsRule r = new JenkinsRule();

    // TODO: Move to jenkins-test-harness
    @JenkinsRecipe(WithCustomLocalPluginManager.RuleRunnerImpl.class)
    @Target(METHOD)
    @Retention(RUNTIME)
    public @interface WithCustomLocalPluginManager {
        Class<? extends LocalPluginManager> value();

        class RuleRunnerImpl extends JenkinsRecipe.Runner<WithCustomLocalPluginManager> {
            private String oldValue;

            @Override
            public void setup(JenkinsRule jenkinsRule, WithCustomLocalPluginManager recipe) throws Exception {
                jenkinsRule.useLocalPluginManager = true;
                oldValue = System.getProperty(LocalPluginManager.CUSTOM_PLUGIN_MANAGER);
                System.setProperty(LocalPluginManager.CUSTOM_PLUGIN_MANAGER, recipe.value().getName());

            }

            @Override
            public void tearDown(JenkinsRule jenkinsRule, WithCustomLocalPluginManager recipe) throws Exception {
                System.setProperty(LocalPluginManager.CUSTOM_PLUGIN_MANAGER, oldValue);
            }
        }
    }

    @Issue("JENKINS-34681")
    @WithPlugin("tasks.jpi")
    @WithCustomLocalPluginManager(CustomPluginManager.class)
    @Test public void customPluginManager() {
        assertTrue("Correct plugin manager installed", r.getPluginManager() instanceof CustomPluginManager);
        assertNotNull("Plugin 'tasks' installed", r.jenkins.getPlugin("tasks"));
    }

    public static class CustomPluginManager extends LocalPluginManager {
        public CustomPluginManager(Jenkins jenkins) {
            super(jenkins);
        }
    }

}
