package jenkins.install;

import java.util.Iterator;
import java.util.List;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * Allows plugging in to the lifecycle when determining InstallState
 * from {@link InstallUtil#getInstallState()}
 */
public abstract class InstallStateFilter implements ExtensionPoint {
    /**
     * Determine the current or next install state, proceed with `return proceed.next()`
     */
    public abstract InstallState getInstallState(InstallState current, Iterator<InstallState> proceed);
    
    /**
     * Get all the InstallStateFilters, in extension order
     */
    public static List<InstallStateFilter> all() {
        return ExtensionList.lookup(InstallStateFilter.class);
    }
}
