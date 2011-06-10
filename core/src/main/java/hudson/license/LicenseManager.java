package hudson.license;

import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.util.FormValidation;
import hudson.util.TimeUnit2;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the license key.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class LicenseManager extends ManagementLink implements Describable<LicenseManager>, Saveable, StaplerProxy {
    /**
     * PEM encoded RSA private key
     */
    private String key;

    /**
     * PEM encoded X509 certificate
     */
    private String certificate;

    private transient License parsed;

    private long expires;

    public LicenseManager() {
        try {
            XmlFile xml = getConfigFile();
            if (xml.exists())
                xml.unmarshal(this);
            if (key!=null || certificate!=null) // no key set yet. no point in parsing the license.
                parse();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read the existing license",e);
        }
    }

    public String getKey() {
        return key;
    }

    public String getCertificate() {
        return certificate;
    }

    /**
     * Gets the parsed license object.
     * Null if we have no license.
     */
    public License getParsed() {
        return parsed;
    }

    /**
     * When does the evaluation expire?
     */
    public long getExpires() {
        return expires;
    }

    @Override
    public String getIconFileName() {
        return "secure.gif";
    }

    @Override
    public String getUrlName() {
        return "license";
    }

    public String getDisplayName() {
        return "Manage License";
    }

    @Override
    public String getDescription() {
        return "Enter the license key for this Hudson";
    }

    public Descriptor<LicenseManager> getDescriptor() {
        return Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Only sysadmin can access this page.
     */
    public Object getTarget() {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        return this;
    }

    public HttpResponse doConfigSubmit(StaplerRequest req) throws ServletException, IOException {
        setLicense(req.getSubmittedForm().getString("key"),
                   req.getSubmittedForm().getString("certificate"));
        return HttpResponses.redirectToContextRoot(); // send the user back to the top page
    }

    public void setLicense(String key, String certificate) throws ServletException, IOException {
        this.key = key;
        this.certificate = certificate;
        save();
        parse();
    }

    public void save() throws IOException {
        getConfigFile().write(this);
    }

    private void parse() throws IOException {
        parsed = null;
        expires = 0;
        try {
            parsed = new License(key, certificate);
            expires = parsed.getExpirationDate();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    public static XmlFile getConfigFile() {
        return new XmlFile(new File(Hudson.getInstance().getRootDir(),"license.xml"));
    }

    /**
     * Has the license expired?
     */
    public boolean isExpired() {
        return getRemainingDays()<0;
    }

    /**
     * Gets the remaining days that the license/evaluation is available
     */
    int getRemainingDays() {
        long d;
        long now = System.currentTimeMillis();
        if (parsed==null)
            d = expires -now;
        else
            d = parsed.getExpirationDate()-now;
        return (int)TimeUnit2.MILLISECONDS.toDays(d);
    }

    /**
     * Displayed to the footer.
     */
    public String getFooterString() {
        String s;
        if (parsed==null) {
            s = "Evaluation License";
        } else {
            s = "Licensed to "+parsed.getCustomerName();
            String ou = parsed.getOrganizationalUnit();
            if (ou!=null)
                s += " - "+ou;
        }
        int d = getRemainingDays();
        if (d<0)
            s += " (Expired)";
        else
        if (d<60)
            s += " ("+d+" more days)";
        return s;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<LicenseManager> {
        @Override
        public String getDisplayName() {
            return "";
        }

        /**
         * Checks the validity of the license key.
         */
        public FormValidation doValidate(@QueryParameter String key, @QueryParameter String certificate) throws IOException, GeneralSecurityException {
            License lic = new License(key, certificate);
            return FormValidation.ok("Licensed to "+lic.getCustomerName()+"\nValid until "+ SimpleDateFormat.getDateInstance().format(lic.getExpirationDate()));
        }
    }

    /**
     * Use the installation-unique secret key as the seed of the PKCS12 file key.
     */
    public static String getHudsonIdHash() {
        return Util.getDigestOf(Hudson.getInstance().getSecretKey());
    }

    public static LicenseManager getInstance() {
        return all().get(LicenseManager.class);
    }

    private static final Logger LOGGER = Logger.getLogger(LicenseManager.class.getName());
}
