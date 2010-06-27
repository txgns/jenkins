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
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

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

    public boolean verified(String licenseKey) {
        //is this licenseKey valid for this installation? -- Note that here, the license file may not be present, we need to do a live verification
        return true; //for now, trust any key, TODO
    }

    // VIEW METHODS

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        if (!isLicenseValid()) {
            rsp.setStatus(SC_UNAUTHORIZED);
            req.getView(this, "index").forward(req, rsp);
        }
    }

    public void doRegister(StaplerRequest request, StaplerResponse response, @QueryParameter int licensingMethod,
                           @QueryParameter String userName, @QueryParameter String password, @QueryParameter String email,
                           @QueryParameter String company, @QueryParameter String subscribe,
                           @QueryParameter String licenseKey) throws IOException, ServletException {
        //user has clicked on register button
        JSONObject j = toRegistrationData(userName, password, email, company, subscribe);
        setPayload(request, j);
        request.getView(this, "response").forward(request, response);
    }

    public void doDone(StaplerRequest request, StaplerResponse response) throws IOException, ServletException {
        //called for the last step
        writeLicenseKey(request.getParameter("privateKey"), request.getParameter("cert"));
        resetToHudson();
        request.setAttribute("rootUrl", Functions.inferHudsonURL(request));
        request.getView(this, "done").forward(request, response);
    }

    private void resetToHudson() {
        //an all important method
        context.setAttribute("app", Hudson.getInstance());
    }

    private void setPayload(StaplerRequest request, JSONObject j) throws UnsupportedEncodingException {
        //http://license.infradna.com/
        String up = "http://localhost:9090/register?data=" + URLEncoder.encode(new String(Base64.encode(j.toString().getBytes())), "UTF-8");
        request.setAttribute("urlAndPayload", up);
    }

    private JSONObject toRegistrationData(String userName, String password, String email, String company, String subscribe) {
        JSONObject j = new JSONObject();
        j.put("userName", userName);
        j.put("password", password);
        j.put("email", email);
        j.put("company", company);
        j.put("subscribe", subscribe);
        j.put("hudsonIdHash", Util.getDigestOf(Hudson.getInstance().getSecretKey()));
        return j;
    }

    private void writeLicenseKey(String privateKey, String cert) throws IOException {
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
    }
}
