package hudson.license;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.ManagementLink;
import hudson.model.PageDecorator;
import hudson.util.TimeUnit2;

/**
 * Implements the functionality to remind (nag) the users to buy a support license.
 *
 * @author Kedar Mhaswade (km@infradna.com)
 *         Date: Jun 30, 2010
 */
@Extension
public class Reminder extends PageDecorator {

    private volatile LicenseManager lm;
    private volatile long lastNagTime;
    public Reminder() {
        super(Reminder.class);
    }

    public boolean isDue() {
        findLicenseManager();
        long last = lastNagTime;
        long now  = System.currentTimeMillis();
        boolean nag = false;
        if (TimeUnit2.MILLISECONDS.toDays(now-last) > 1) {
            nag = true;
            lastNagTime = now;
        }
        if (lm.getRemainingDays() <= 60 && nag) { //getting nagged is NOT optional, you will get nagged daily :-)
            return true;
        }
        return false;
    }

    private void findLicenseManager() {
        if (lm == null) {
            ExtensionList<ManagementLink> lms = LicenseManager.all();
            lm = lms.get(LicenseManager.class); //hope this is not null
        }
    }
}
