package jenkins.install;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

/**
 * All adjuncts will be included when the setup wizard is.
 * In order to hook into the setup wizard lifecycle, you should
 * include something in a script that call
 * to `setupWizardExtension` with a callback, for example:
 * <pre>
 * setupWizardExtension(function($wizard){
 *   $wizard.addTemplate(require('./templates/someTemplate.hbs'));
 *   $wizard.on('click', '.saveSomeTemplate', function() {
 *     // work here
 *   });
 * });
 * </pre>
 */
public abstract class SetupWizardExtension implements ExtensionPoint {
    public Class<?> getExtensionType() {
        return SetupWizard.class;
    }
    
    /**
     * Determines if the instance appears to be offline
     */
    public boolean isOffline(Iterator<Boolean> next) {
        return next.next();
    }
    
    /**
     * Determine the current or next install state, proceed with `return proceed.next()`
     */
    public abstract InstallState getInstallState(InstallState current, Iterator<InstallState> proceed);
    
    /**
     * Used to provide an alternate start panel
     * based on some other logic
     */
    public String getStartPanel(Iterator<String> next) {
        return next.next();
    }
    
    /**
     * Get the current InstallState, which may be plugin-defined
     */
    public InstallState getCurrentState(Iterator<InstallState> next) {
        return next.next();
    }
    
    public static List<SetupWizardExtension> all() {
        return ExtensionList.lookup(SetupWizardExtension.class);
    }
    
    /**
     * Get setup wizard extensions
     */
    public static List<Object> getExtensions() {
        List<Object> out = new ArrayList<>();
        for(SetupWizardExtension ext : SetupWizardExtension.all()) {
            out.add(Jenkins.getInstance().getInjector().getInstance(ext.getExtensionType()));
        }
        // always add the default setup wizard last
        return out;
    }
    
}