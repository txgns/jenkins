package hudson.license;

import com.trilead.ssh2.crypto.Base64;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;

/**
 * @author Kohsuke Kawaguchi
 */
public class RegistrationData extends AbstractDescribableImpl<RegistrationData> {
    public String userName;
    public String email;
    public String company;
    public String password;
    public boolean subscribe = true;

    public String key;
    public String cert;

    public int value;

    /**
     * If this registration data is incomplete/inconsistent, indicate that error.
     */
    public String message;

    public boolean isValid() {
        return message==null;
    }

    public ServerResponse getManualLicense() throws IOException {
        return new ServerResponse(key,cert,null);
    }

    private JSONObject toRegistrationData() {
        JSONObject j = new JSONObject();
        j.put("userName", Util.fixNull(userName));
        j.put("password", Util.fixNull(password));
        j.put("email", Util.fixNull(email));
        j.put("company", Util.fixNull(company));
        j.put("subscribe", subscribe);
        j.put("hudsonIdHash", getHudsonIdHash());
        return j;
    }

    @Override
    public String toString() {
        return toRegistrationData().toString();
    }

    /**
     * URL that we should hit to send the registration data to the server.
     */
    public URL getRegistrationURL() throws IOException {
        // TODO: encyrpt the payload
        return new URL("http://license.infradna.com/register?data=" + new String(Base64.encode(toRegistrationData().toString().getBytes())));
    }

    public void doRegister(StaplerRequest request, StaplerResponse response) throws IOException, ServletException, GeneralSecurityException {
        request.bindJSON(this, request.getSubmittedForm().getJSONObject("method"));

        try {
            getDescriptor().doCheckEmail(email);
            getDescriptor().doCheckPassword(password);
            getDescriptor().doCheckCompany(company);
            message = null;
        } catch (FormValidation e) {
            // TODO: improve the core so that we can get the message out here
            message = "Correct the input and retry";
        }

        String nextStep = "index";
        //user has clicked on register button
        switch (value) {
        case 1: // server generated
            nextStep = isValid()?"response":"index";
            break;
        case 2: // manually entered
            try {
                ServerResponse rsp = getManualLicense();
                rsp.save();
                request.getView(rsp,"index").forward(request,response);
                return;
            } catch (Exception e) {
                message = Functions.printThrowable(e);
            }
            break;
        default:
            //came here in error?
            break;
        }
        request.getView(this, nextStep).forward(request, response);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    // to make it easier to retrieve this value from Jelly
    public static String getHudsonIdHash() {
        return LicenseManager.getHudsonIdHash();
    }


    @Extension
    public static final class DescriptorImpl extends Descriptor<RegistrationData> {
        @Override
        public String getDisplayName() {
            return "";  // unused
        }

        public FormValidation doCheckEmail(@QueryParameter(fixEmpty = true) String email) throws FormValidation {
            if (email == null || email.indexOf('@') < 0)
                throw FormValidation.error("Provide a valid email");
            return FormValidation.ok();
        }

        public FormValidation doCheckPassword(@QueryParameter(fixEmpty = true) String password) throws FormValidation {
            if (password == null)
                throw FormValidation.error("Provide a Password");
            return FormValidation.ok();
        }

        public FormValidation doCheckCompany(@QueryParameter(fixEmpty = true) String company) throws FormValidation {
            if (company == null)
                throw FormValidation.error("Provide a company name");
            return FormValidation.ok();
        }
    }
}
