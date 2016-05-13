/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
package jenkins.install;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.inject.Provider;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

/**
 * Jenkins install state.
 *
 * In order to hook into the setup wizard lifecycle, you should
 * include something in a script that call
 * to `onSetupWizardInitialized` with a callback, for example:
 * 
 * <pre>
 * onSetupWizardInitialized(function($wizard){
 *   $wizard.addTemplate(require('./templates/someTemplate.hbs'));
 *   $wizard.on('click', '.saveSomeTemplate', function() {
 *     // work here
 *   });
 * });
 * </pre>
 * 
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class InstallState implements ExtensionPoint {
    /**
     * Need InstallState != NEW for tests by default
     */
    @Extension
    public static final InstallState UNKNOWN = new InstallState("UNKNOWN", true, null);
    
    /**
     * The initial set up has been completed
     */
    @Extension
    public static final InstallState INITIAL_SETUP_COMPLETED = new InstallState("INITIAL_SETUP_COMPLETED", true, null) {
        public void initializeState() {
            Jenkins j = Jenkins.getInstance();
            try {
                j.getSetupWizard().completeSetup(j);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };
    
    /**
     * Creating an admin user for an initial Jenkins install.
     */
    @Extension
    public static final InstallState CREATE_ADMIN_USER = new InstallState("CREATE_ADMIN_USER", false, INITIAL_SETUP_COMPLETED);
    
    /**
     * New Jenkins install. The user has kicked off the process of installing an
     * initial set of plugins (via the install wizard).
     */
    @Extension
    public static final InstallState INITIAL_PLUGINS_INSTALLING = new InstallState("INITIAL_PLUGINS_INSTALLING", false, CREATE_ADMIN_USER);
    
    /**
     * New Jenkins install.
     */
    @Extension
    public static final InstallState NEW = new InstallState("NEW", false, INITIAL_PLUGINS_INSTALLING) {
        public void initializeState() {
            try {
                Jenkins.getInstance().getSetupWizard().init(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    };
    
    /**
     * Restart of an existing Jenkins install.
     */
    @Extension
    public static final InstallState RESTART = new InstallState("RESTART", true, INITIAL_SETUP_COMPLETED) {
        public void initializeState() {
            InstallUtil.saveLastExecVersion();
        }
    };
    
    /**
     * Upgrade of an existing Jenkins install.
     */
    @Extension
    public static final InstallState UPGRADE = new InstallState("UPGRADE", true, INITIAL_SETUP_COMPLETED) {
        public void initializeState() {
            SetupWizard wiz = Jenkins.getInstance().getSetupWizard();
            if (!wiz.getPlatformPluginUpdates().isEmpty()) {
                try {
                    UpgradeWizard uw = new UpgradeWizard();
                    Jenkins.getInstance().setInstallState(uw);
                    uw.initializeState();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    };
    
    /**
     * Downgrade of an existing Jenkins install.
     */
    @Extension
    public static final InstallState DOWNGRADE = new InstallState("DOWNGRADE", true, INITIAL_SETUP_COMPLETED) {
        public void initializeState() {
            InstallUtil.saveLastExecVersion();
        }
    };
    
    /**
     * Jenkins started in test mode (JenkinsRule).
     */
    public static final InstallState TEST = new InstallState("TEST", true, INITIAL_SETUP_COMPLETED);
    
    /**
     * Jenkins started in development mode: Bolean.getBoolean("hudson.Main.development").
     * Can be run normally with the -Djenkins.install.runSetupWizard=true
     */
    public static final InstallState DEVELOPMENT = new InstallState("DEVELOPMENT", true, INITIAL_SETUP_COMPLETED);

    private final boolean isSetupComplete;
    private final InstallState nextState;
    private final String name;

    public InstallState(@Nonnull String name, boolean isSetupComplete, InstallState nextState) {
        this.name = name;
        this.isSetupComplete = isSetupComplete;
        this.nextState = nextState;
    }
    
    /**
     * Process any initialization this install state requires
     */
    public void initializeState() {
    }

    /**
     * Indicates the initial setup is complete
     */
    public boolean isSetupComplete() {
        return isSetupComplete;
    }
    
    /**
     * Gets the next state
     */
    public void proceedToNextState() {
        // Pass to extensions for any customizations to the state transitions
        InstallState next = InstallUtil.getNextInstallState(this);
        if(next == null) {
            // fall back to setting the next state defined locally
            next = nextState;
        }
        Jenkins.getInstance().setInstallState(next == null ? UNKNOWN : next);
    }
    
    /**
     * Used to provide an alternate start panel
     * based on some other logic
     */
    public String getStartPanel(Provider<String> next) {
        return next.get();
    }
    
    public String name() {
        return name;
    }
    
    @Override
    public int hashCode() {
        return name.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if(obj instanceof InstallState) {
            return name.equals(((InstallState)obj).name());
        }
        return false;
    }
    
    @Override
    public String toString() {
        return "InstallState (" + name + ")";
    }

    /**
     * Find an install state by name
     * @param name
     * @return
     */
    public static InstallState valueOf(String name) {
        for(InstallState state : all()) {
            if(name.equals(state.name)) {
                return state;
            }
        }
        return null;
    }

    /**
     * Returns all install states in the system
     */
    static ExtensionList<InstallState> all() {
        return ExtensionList.lookup(InstallState.class);
    }
}
