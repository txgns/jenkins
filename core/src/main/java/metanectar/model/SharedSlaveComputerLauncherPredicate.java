package metanectar.model;

import hudson.slaves.ComputerLauncher;

/**
 * Checks that the supplied {@link ComputerLauncher} descriptors are ones that can be used for {@link SharedSlave}s.
 *
 * @author Stephen Connolly
 */
public final class SharedSlaveComputerLauncherPredicate extends DescriptorBlackListPredicate<ComputerLauncher> {

    private static final class ResourceHolder {
        private static final SharedSlaveComputerLauncherPredicate INSTANCE = new SharedSlaveComputerLauncherPredicate
                ();
    }

    public static SharedSlaveComputerLauncherPredicate getInstance() {
        return ResourceHolder.INSTANCE;
    }

    private SharedSlaveComputerLauncherPredicate() {
    }

}
