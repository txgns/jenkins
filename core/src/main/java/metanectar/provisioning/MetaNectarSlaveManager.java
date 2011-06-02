package metanectar.provisioning;

import com.cloudbees.commons.metanectar.provisioning.ComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.ExportableFuture;
import com.cloudbees.commons.metanectar.provisioning.FutureComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.SerializableLabel;
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
 * TODO this is currently a dummy class.
 *
 * @author Paul Sandoz
 */
public class MetaNectarSlaveManager implements SlaveManager {
    private int n;

    public boolean canProvision(SerializableLabel label) throws IOException, InterruptedException {
        return label.matches(new VariableResolver<Boolean>() {
            public Boolean resolve(String name) {
                return name.equals("foo");
            }
        });
    }

    public Collection<SerializableLabel> getLabels() {
        return Collections.singleton(new SerializableLabel("foo"));
    }

    public FutureComputerLauncherFactory provision(SerializableLabel label, final TaskListener listener, int numOfExecutors) throws IOException, InterruptedException {
        listener.getLogger().println("MN: Started provisioning");
        final Future<ComputerLauncherFactory> task = Hudson.MasterComputer.threadPoolForRemoting.submit(new Callable<ComputerLauncherFactory>() {
            public ComputerLauncherFactory call() throws Exception {
                Thread.sleep(3000);
                listener.getLogger().println("MN: Still provisioning");
                Thread.sleep(3000);
                listener.getLogger().println("MN: Done provisioning");
                return new ResultImpl();
            }
        });

        return new FutureComputerLauncherFactory("slave"+(n++), 1, new ExportableFuture<ComputerLauncherFactory>(task));
    }

    private static class ResultImpl extends ComputerLauncherFactory {
        public ComputerLauncher getOrCreateLauncher() throws IOException, InterruptedException {
            try {
                System.out.println( "Launching Slave Computer..." );
                String command = String.format( "\"%s/bin/java\" -jar \"%s\"", System.getProperty( "java.home" ),
                                               new File( Hudson.getInstance().getJnlpJars(
                                                   "slave.jar" ).getURL().toURI() ).getAbsolutePath() );
                System.out.println( "... Command is " + command );
                return new CommandLauncher( command );
            } catch (URISyntaxException e) {
                // during the test we always find slave.jar in the file system
                throw new AssertionError(e);
            }
        }
    }
}
