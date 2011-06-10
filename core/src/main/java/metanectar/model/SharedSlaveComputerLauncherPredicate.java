package metanectar.model;

import com.sun.istack.internal.Nullable;
import hudson.model.Descriptor;
import hudson.slaves.ComputerLauncher;
import metanectar.MetaNectarExtensionPoint;

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

    @Override
    public boolean apply(@Nullable Descriptor<ComputerLauncher> input) {
        return super.apply(input) && input != null && !MetaNectarExtensionPoint.class.isAssignableFrom(input.clazz);
    }
}
