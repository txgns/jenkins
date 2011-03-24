package metanectar.provisioning;

import com.cloudbees.commons.metanectar.provisioning.ProvisioningActivity;
import com.cloudbees.commons.metanectar.provisioning.ProvisioningResult;
import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.remoting.RemoteFuture;
import hudson.slaves.CommandLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.util.VariableResolver;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author Paul Sandoz
 */
public class MetaNectarSlaveManager implements SlaveManager {
    private int n;

    public boolean canProvision(Label label) throws IOException, InterruptedException {
        return label.matches(new VariableResolver<Boolean>() {
            public Boolean resolve(String name) {
                return name.equals("foo");
            }
        });
    }

    public Collection<LabelAtom> getLabels() {
        return Collections.singleton(new LabelAtom("foo"));
    }

    public ProvisioningActivity provision(Label label, final TaskListener listener, int numOfExecutors) throws IOException, InterruptedException {
        listener.getLogger().println("MN: Started provisioning");
        final Future<ProvisioningResult> task = Hudson.MasterComputer.threadPoolForRemoting.submit(new Callable<ProvisioningResult>() {
            public ProvisioningResult call() throws Exception {
                Thread.sleep(3000);
                listener.getLogger().println("MN: Still provisioning");
                Thread.sleep(3000);
                listener.getLogger().println("MN: Done provisioning");
                return new ResultImpl();
            }
        });

        return new ProvisioningActivity("slave"+(n++), 1, new RemoteFuture<ProvisioningResult>(task));
    }

    private static class ResultImpl extends ProvisioningResult {
        public ComputerLauncher getLauncher() throws IOException, InterruptedException {
            try {
                return new CommandLauncher(
                        String.format("\"%s/bin/java\" -jar \"%s\"",
                                System.getProperty("java.home"),
                                new File(Hudson.getInstance().getJnlpJars("slave.jar").getURL().toURI()).getAbsolutePath()));
            } catch (URISyntaxException e) {
                // during the test we always find slave.jar in the file system
                throw new AssertionError(e);
            }
        }
    }
}
