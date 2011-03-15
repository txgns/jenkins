package metanectar.provisioning;

import hudson.model.Label;
import hudson.model.TaskListener;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public interface MetaNectarSlaveManager {
    boolean canProviosion(Label label, int numOfExecutors) throws IOException, InterruptedException;
    ProvisioningInProgress provision(Label label, TaskListener listener, int numOfExecutors) throws IOException, InterruptedException;
}
