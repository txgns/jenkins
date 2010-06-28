package hudson.license;

import com.trilead.ssh2.crypto.Base64;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.DescriptorByNameOwner;
import hudson.model.Hudson;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

/**
 * Handles Registration. Could be modified to be discoverable.
 *
 * @author Kedar Mhaswade (km@infradna.com)
 *         Date: Jun 19, 2010
 */
public final class RegistrationHandler extends AbstractDescribableImpl<RegistrationHandler> implements DescriptorByNameOwner {

    private final ServletContext context;
    private static RegistrationHandler instance;

    public synchronized static RegistrationHandler instance(ServletContext context) {
        instance = new RegistrationHandler(context);
        return instance;
    }

    private RegistrationHandler(ServletContext context) {
        this.context = context;
    }

    public boolean isLicenseValid() throws IOException {
        //is this licenseKey valid for this installation?
        if (!LicenseManager.getConfigFile().exists())
            return false;
        return !(new LicenseManager().isExpired());
    }

    // VIEW METHODS

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        if (!isLicenseValid()) {
            rsp.setStatus(SC_UNAUTHORIZED);
            req.getView(this, "index").forward(req, rsp);
        }
    }

    public void doRegister(StaplerRequest request, StaplerResponse response, @QueryParameter int method,
                           @QueryParameter String userName, @QueryParameter String password, @QueryParameter String email,
                           @QueryParameter String company, @QueryParameter String subscribe,
                           @QueryParameter String key, @QueryParameter String cert) throws IOException, ServletException, GeneralSecurityException {
        //user has clicked on register button
        if (method == 1) {//server generated
            JSONObject j = toRegistrationData(userName, password, email, company, subscribe);
            setPayload(request, j);
            request.getView(this, "response").forward(request, response);
        } else if (method == 2) { //
            LicenseManager lm = new LicenseManager(key, cert);
            if (lm.isExpired()) {
                request.setAttribute("message", "License expired, retry");
                request.getView(this, "index").forward(request, response);
            }
            else //we are good
                resetToHudson(request, response);
        } else {
            //came here in error?
            request.getView(this, "index").forward(request, response);
        }
    }

    public void doDone(StaplerRequest request, StaplerResponse response) throws IOException, ServletException, GeneralSecurityException {
        //called for the last step
        writeLicenseKey(request.getParameter("licenseKey"), request.getParameter("cert"));
        resetToHudson(request, response);
    }

    public void doDynamic(StaplerRequest request, StaplerResponse response) throws IOException, ServletException {
        String rest = request.getRestOfPath();
        //radioBlock help handling hack without any side-effects (I think)
        if (rest.startsWith("/help-")) {
            request.getView(this, rest.substring(1)).forward(request, response); //just remove the leading '/'
        }
    }
    //VIEW METHODS

    private void resetToHudson(StaplerRequest request, StaplerResponse response) throws IOException, ServletException {
        //an all important method
        context.setAttribute("app", Hudson.getInstance());
        request.setAttribute("rootUrl", Functions.inferHudsonURL(request));
        request.getView(this, "done").forward(request, response);
    }

    private void setPayload(StaplerRequest request, JSONObject j) throws UnsupportedEncodingException {
        //http://license.infradna.com/                          TODO
        String up = "http://localhost:9090/register?data=" + URLEncoder.encode(new String(Base64.encode(j.toString().getBytes())), "UTF-8");
        request.setAttribute("urlAndPayload", up);
    }

    private JSONObject toRegistrationData(String userName, String password, String email, String company, String subscribe) {
        JSONObject j = new JSONObject();
        j.put("userName", Util.fixNull(userName));
        j.put("password", Util.fixNull(password));
        j.put("email", Util.fixNull(email));
        j.put("company", Util.fixNull(company));
        j.put("subscribe", Util.fixNull(subscribe));
        j.put("hudsonIdHash", Util.getDigestOf(Hudson.getInstance().getSecretKey()));
        return j;
    }

    private void writeLicenseKey(String privateKey, String cert) throws IOException, GeneralSecurityException {
        //we are going to do this at most once
        //these strings are base64 encoded
        privateKey = new String(Base64.decode(privateKey.toCharArray()));
        cert = new String(Base64.decode(cert.toCharArray()));
        LicenseManager lm = new LicenseManager(privateKey, cert);
        lm.save();
    }

    public String getDisplayName() {
        return "";
    }

    @Override
    public Descriptor getDescriptor() {
        return getDescriptorByName(this.getClass().getName());
    }

    public Descriptor getDescriptorByName(String className) {
        return Hudson.getInstance().getDescriptorByName(className);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RegistrationHandler> {
        @Override
        public String getDisplayName() {
            return ""; // not used
        }

        public FormValidation doCheckUserName(@QueryParameter(fixEmpty = true) String userName) {
            if (userName == null)
                return FormValidation.error("Provide a User Name");
            return FormValidation.ok();
        }
        public FormValidation doCheckEmail(@QueryParameter(fixEmpty = true) String email) {
            if (email == null)
                return FormValidation.error("Provide a valid email");
            if (email.indexOf('@') < 0)
                return FormValidation.error("Invalid email address");
            return FormValidation.ok();
        }
        public FormValidation doCheckPassword(@QueryParameter(fixEmpty = true) String password) {
            if (password== null)
                return FormValidation.error("Provide a Password");
            return FormValidation.ok();
        }
    }
}
