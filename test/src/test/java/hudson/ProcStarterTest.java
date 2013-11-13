/*
 * The MIT License
 *
 * Copyright 2013 Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
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

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.util.concurrent.Future;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import hudson.Launcher.ProcStarter;
import hudson.Launcher.DecoratedLauncher;

/**
 * Contains tests for {@ProcStarter} class.
 * @author Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 * @since TODO: define version
 */
public class ProcStarterTest extends HudsonTestCase {

    @Bug(20559)
    public void testNonInitializedEnvsNPE() throws Exception {
        // Create nodes and other test stuff
        hudson.setNumExecutors(0);
        createSlave();

        // Wrapper, which contains a nested launch
        BuildWrapper w1 = new BuildWrapper() {
            @Override
            public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                Launcher.ProcStarter starter = launcher.launch().cmds("echo", "Hello");
                starter.start();
                starter.join();
                return new Environment() {
                };
            }

            @Extension
            class DescriptorImpl extends TestWrapperDescriptor {
            }
        };

        // A second wrapper with a LauncherDecorator
        BuildWrapper w2 = new BuildWrapper() {
            @Override
            public Launcher decorateLauncher(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
                return new DecoratedLauncher(launcher) {
                    @Override
                    public Proc launch(Launcher.ProcStarter starter) throws IOException {
                        starter.envs(); // Finally, call envs()
                        return super.launch(starter);
                    }
                };
            }

            @Extension
            class DescriptorImpl extends TestWrapperDescriptor {
            }
        };

        // Create a job with build wrappers above
        FreeStyleProject project = createFreeStyleProject();
        project.getBuildWrappersList().add(w1);
        project.getBuildWrappersList().add(w2);

        // Run the build. If NPE occurs, the test will fail
        Future<FreeStyleBuild> build = project.scheduleBuild2(0);
        assertBuildStatusSuccess(build);
    }

    public abstract static class TestWrapperDescriptor extends BuildWrapperDescriptor {
        
        @Override
        public boolean isApplicable(AbstractProject<?, ?> ap) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "testStub";
        }
    }
}
