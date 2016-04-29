package jenkins.install;

import hudson.Extension;
import hudson.model.PageDecorator;
import hudson.util.HttpResponses;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;

import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import static org.apache.commons.io.FileUtils.*;
import static org.apache.commons.lang.StringUtils.*;

/**
 * This is a stop-gap measure until JENKINS-33663 comes in.
 * This call may go away. Please don't use it from outside.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
@Extension
public class UpgradeWizard extends PageDecorator {
    @Inject
    private Jenkins jenkins;

    /**
     * Is this instance fully upgraded?
     */
    private volatile boolean upToDate;
    
    /**
     * Whether to show the upgrade wizard
     */
    private static final String SHOW_UPGRADE_WIZARD_FLAG = UpgradeWizard.class.getName() + ".show";

    /**
     * File that captures the state of upgrade.
     *
     * This file records the vesrion number that the installation has upgraded to.
     */
    /*package*/ File getStateFile() {
        return new File(Jenkins.getInstance().getRootDir(),"jenkins.install.UpgradeWizard.state");
    }

    public UpgradeWizard() throws IOException {
        updateUpToDate();
    }

    private void updateUpToDate() throws IOException {
        upToDate = new VersionNumber("2.0").compareTo(getCurrentLevel()) <= 0;
    }

    /**
     * What is the version the upgrade wizard has run the last time and upgraded to?
     */
    private VersionNumber getCurrentLevel() throws IOException {
        VersionNumber from = new VersionNumber("1.0");
        File state = getStateFile();
        if (state.exists()) {
            from = new VersionNumber(defaultIfBlank(readFileToString(state), "1.0").trim());
        }
        return from;
    }

    /*package*/
    void setCurrentLevel(VersionNumber v) throws IOException {
        FileUtils.writeStringToFile(getStateFile(), v.toString());
        updateUpToDate();
    }
    
    static void completeUpgrade(Jenkins jenkins) throws IOException {
        // this was determined to be a new install, don't run the update wizard here
        UpgradeWizard uw = jenkins.getInjector().getInstance(UpgradeWizard.class);
        if (uw!=null)
            uw.setCurrentLevel(new VersionNumber("2.0"));
    }

    /**
     * Do we need to show the upgrade wizard prompt?
     */
    public boolean isDue() {
        if (upToDate)
            return false;

        // only admin users should see this
        if (!jenkins.hasPermission(Jenkins.ADMINISTER))
            return false;

        // only show when Jenkins is fully up & running
        WebApp wa = WebApp.getCurrent();
        if (wa==null || !(wa.getApp() instanceof Jenkins))
            return false;

        return System.currentTimeMillis() > getStateFile().lastModified();
    }
    
    /**
     * Whether to show the upgrade wizard
     */
    public boolean isShowUpgradeWizard() {
        HttpSession session = Stapler.getCurrentRequest().getSession(false);
        if(session != null) {
            return Boolean.TRUE.equals(session.getAttribute(SHOW_UPGRADE_WIZARD_FLAG));
        }
        return false;
    }
    /**
     * Call this to show the upgrade wizard
     */
    public HttpResponse doShowUpgradeWizard() throws Exception {
        jenkins.checkPermission(Jenkins.ADMINISTER);
        HttpSession session = Stapler.getCurrentRequest().getSession(true);
        session.setAttribute(SHOW_UPGRADE_WIZARD_FLAG, true);
        jenkins.setSetupWizard(new SetupWizard(jenkins, false));
        return HttpResponses.redirectToContextRoot();
    }
    
    /**
     * Call this to hide the upgrade wizard
     */
    public HttpResponse doHideUpgradeWizard() {
        jenkins.checkPermission(Jenkins.ADMINISTER);
        HttpSession session = Stapler.getCurrentRequest().getSession(false);
        if(session != null) {
            session.removeAttribute(SHOW_UPGRADE_WIZARD_FLAG);
        }
        jenkins.setSetupWizard(null);
        return HttpResponses.redirectToContextRoot();
    }

    /**
     * Snooze the upgrade wizard notice.
     */
    @RequirePOST
    public HttpResponse doSnooze() throws IOException {
        jenkins.checkPermission(Jenkins.ADMINISTER);
        File f = getStateFile();
        FileUtils.touch(f);
        f.setLastModified(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        LOGGER.log(FINE, "Snoozed the upgrade wizard notice");
        return HttpResponses.redirectToContextRoot();
    }

    /**
     * Performs the upgrade activity.
     */
    @RequirePOST
    public HttpResponse doUpgrade() throws IOException {
        jenkins.checkPermission(Jenkins.ADMINISTER);
        try {
            if (new VersionNumber("2.0").compareTo(getCurrentLevel())>0) {
                // 1.0 -> 2.0 upgrade
                LOGGER.log(WARNING, "Performing 1.0 to 2.0 upgrade");

                List<String> installing = Arrays.asList("workflow-aggregator", "pipeline-stage-view", "github-organization-folder");
                jenkins.getPluginManager().install(installing, true);
                // return the installing plugins
                return HttpResponses.okJSON(JSONArray.fromObject(installing));
            }

//      in the future...
//        if (new VersionNumber("3.0").compareTo(getCurrentLevel())>0) {
//
//        }

            return NOOP;
        } finally {
            updateUpToDate();
        }
    }

    /*package*/ static final HttpResponse NOOP = HttpResponses.redirectToContextRoot();

    @Extension
    public static class WizardExtension extends SetupWizard.SetupWizardExtension {
        @Override
        public Class<?> getExtensionType() {
            return UpgradeWizard.class;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(UpgradeWizard.class.getName());
}
