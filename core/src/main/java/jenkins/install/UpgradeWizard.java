package jenkins.install;

import hudson.Extension;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;

import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.http.HttpSession;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * This is a stop-gap measure until JENKINS-33663 comes in.
 * This call may go away. Please don't use it from outside.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
@Extension
public class UpgradeWizard extends InstallState {
    /**
     * Is this instance fully upgraded?
     */
    private volatile boolean isUpToDate;
    
    /**
     * Whether to show the upgrade wizard
     */
    private static final String SHOW_UPGRADE_WIZARD_FLAG = UpgradeWizard.class.getName() + ".show";

    public UpgradeWizard() throws IOException {
        super("UPGRADE", false, null);
    }
    
    @Override
    public void init() {
        try {
            updateUpToDate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateUpToDate() throws IOException {
        // If we don't have any platform plugins, it's considered 'up to date' in terms
        // of the updater
        JSONArray platformPlugins = Jenkins.getInstance().getSetupWizard().getPlatformPluginUpdates();
        isUpToDate = platformPlugins.isEmpty();
    }

    /**
     * Do we need to show the upgrade wizard prompt?
     */
    public boolean isDue() {
        if (isUpToDate)
            return false;

        // only admin users should see this
        if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER))
            return false;

        // only show when Jenkins is fully up & running
        WebApp wa = WebApp.getCurrent();
        if (wa==null || !(wa.getApp() instanceof Jenkins))
            return false;

        return System.currentTimeMillis() > Jenkins.getInstance().getSetupWizard().getUpdateStateFile().lastModified();
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
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        HttpSession session = Stapler.getCurrentRequest().getSession(true);
        session.setAttribute(SHOW_UPGRADE_WIZARD_FLAG, true);
        return HttpResponses.redirectToContextRoot();
    }
    
    /**
     * Call this to hide the upgrade wizard
     */
    public HttpResponse doHideUpgradeWizard() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        HttpSession session = Stapler.getCurrentRequest().getSession(false);
        if(session != null) {
            session.removeAttribute(SHOW_UPGRADE_WIZARD_FLAG);
        }
        return HttpResponses.redirectToContextRoot();
    }

    /**
     * Snooze the upgrade wizard notice.
     */
    @RequirePOST
    public HttpResponse doSnooze() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        File f = Jenkins.getInstance().getSetupWizard().getUpdateStateFile();
        FileUtils.touch(f);
        f.setLastModified(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        LOGGER.log(FINE, "Snoozed the upgrade wizard notice");
        return HttpResponses.redirectToContextRoot();
    }

    private static final Logger LOGGER = Logger.getLogger(UpgradeWizard.class.getName());
}
