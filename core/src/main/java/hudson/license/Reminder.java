package hudson.license;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.ManagementLink;
import hudson.model.PageDecorator;
import hudson.util.TimeUnit2;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implements the functionality to remind (nag) the users to buy a support license.
 *
 * @author Kedar Mhaswade (km@infradna.com)
 *         Date: Jun 30, 2010
 */
public class Reminder extends PageDecorator {

    private volatile LicenseManager lm;
    private volatile long lastNagTime;
    private final AtomicBoolean remind = new AtomicBoolean(true);
    private static Reminder theInstance; //guarded by synchronized method
    private Reminder() {
        super(Reminder.class);
    }

    @Extension
    public static synchronized Reminder getInstance() {
        if (theInstance != null)
            return theInstance;
        theInstance = new Reminder();
        return theInstance;
    }
    
    public boolean isDue() {
        if (remind.get() == false)
            return false;
        findLicenseManager();
        long last = lastNagTime;
        long now  = System.currentTimeMillis();
        boolean nag = false;
        if (TimeUnit2.MILLISECONDS.toDays(now-last) > 2 || Boolean.getBoolean("LicenseExpired")) {
            nag = true;
            lastNagTime = now;
        }
        if (lm.getRemainingDays() <= 30 && nag) {
            return true;
        }
        return false;
    }
    
    public int getRemainingDays() {
        return lm.getRemainingDays();
    }

    public void doAct(StaplerRequest request, StaplerResponse response, @QueryParameter (fixEmpty = true) String yes,
                      @QueryParameter (fixEmpty = true) String no) throws IOException, ServletException {
        if (yes != null) {
            response.sendRedirect("http://infradna.com/renew_license");
        } else if (no != null) {
            remind.set(false);
            response.forwardToPreviousPage(request);
        } else { //remind later
            lastNagTime = System.currentTimeMillis();
            response.forwardToPreviousPage(request);
        }
    }

    private void findLicenseManager() {
        if (lm == null) {
            ExtensionList<ManagementLink> lms = LicenseManager.all();
            lm = lms.get(LicenseManager.class); //hope this is not null
        }
    }
}
