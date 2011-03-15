package metanectar.provisioning;

import hudson.slaves.ComputerLauncher;

/**
 * @author Kohsuke Kawaguchi
 */
public interface ProvisioningResult {
    ComputerLauncher getLauncher();
}
