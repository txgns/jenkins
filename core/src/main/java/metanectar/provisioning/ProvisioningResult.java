package metanectar.provisioning;

import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;

import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;

/**
 * Represents the successful outcome of the provisioning.
 *
 * <p>
 * Since this object is meant to be passed across JVMs through remoting, this class is marked as serializable.
 * This one more indirection to {@link ComputerLauncher} works around the fact that most existing
 * {@link ComputerLauncher}s aren't serializable.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ProvisioningResult implements Serializable {
    public abstract ComputerLauncher getLauncher() throws IOException, InterruptedException;

    private static final long serialVersionUID = 1L;
}
