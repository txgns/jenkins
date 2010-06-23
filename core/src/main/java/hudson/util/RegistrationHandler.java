package hudson.util;

import com.trilead.ssh2.crypto.Base64;
import hudson.Functions;
import hudson.Util;
import hudson.WebAppMain;
import hudson.model.Hudson;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.SecureRandom;
import static javax.servlet.http.HttpServletResponse.*;

/** Handles Registration. Could be modified to be discoverable.
 * @author Kedar Mhaswade (km@infradna.com)
 * Date: Jun 19, 2010
 */
public final class RegistrationHandler {

    private final ServletContext context;
    private static RegistrationHandler instance;
    private static final File LICENSE_FILE = new File(Hudson.getInstance().getRootDir(),"license.key");
    private static final int SERVER_GEN = 1;
    private static final int MANUAL = 2;

    public synchronized static RegistrationHandler instance(ServletContext context) {
        instance = new RegistrationHandler(context);
        return instance;
    }

    private RegistrationHandler(ServletContext context) {
        this.context = context;
    }

    public boolean isRegistered() {
        return isLicenseKeyValid();
    }

    public boolean isLicenseKeyValid() {
        //is this licenseKey valid for this installation?        
        return LICENSE_FILE.exists();    //need a better way TODO
    }

    public boolean verified(String licenseKey) {
        //is this licenseKey valid for this installation? -- Note that here, the license file may not be present, we need to do a live verification
        return true; //for now, trust any key, TODO
    }

    // VIEW METHODS
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        if (!isLicenseKeyValid()) {
            rsp.setStatus(SC_UNAUTHORIZED);
            req.getView(this, "index").forward(req, rsp);
        }
    }

    public FormValidation doCheckUserName(@QueryParameter(fixEmpty = true) String userName) {
        if(userName == null)
            return FormValidation.error("Provide a UserName");
        return FormValidation.ok();
    }
    
    public void doRegister(StaplerRequest request, StaplerResponse response, @QueryParameter int licensingMethod,
                         @QueryParameter String userName, @QueryParameter String password, @QueryParameter String email,
                         @QueryParameter String company, @QueryParameter String subscribe,
                         @QueryParameter String licenseKey) throws IOException, ServletException {
        //user has clicked on register button
        if (isServerGenerated(licensingMethod)) {
            JSONObject j = toRegistrationData(userName, password, email, company, subscribe);
            setPayload(request, j);
            request.getView(this, "response").forward(request, response);
        } else if (isManual(licensingMethod)) {
            if (verified(licenseKey)) {
                writeLicenseKey(licenseKey);
                doDone(request, response);
            } else {
                request.setAttribute("message", "Invalid License Key, try again"); //i18n
                request.getView(this, "index").forward(request, response);
            }
        } else { //someone just entered this URL, just forward to index instead of redirect
            request.getView(this, "index").forward(request, response);
        }
    }

    public void doDone(StaplerRequest request, StaplerResponse response) throws IOException, ServletException{
        //called for the last step
        writeLicenseKey(request.getParameter("licenseKey"));
        resetToHudson();
        request.setAttribute("rootUrl", Functions.inferHudsonURL(request));
        request.getView(this, "done").forward(request, response);
    }
    
    private void resetToHudson() {
        //an all important method
        context.setAttribute("app", Hudson.getInstance());
    }

    private void setPayload(StaplerRequest request, JSONObject j) throws UnsupportedEncodingException {
        //http://www.infradna.com/
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
        j.put("hudson-id", Util.getDigestOf(Hudson.getInstance().getSecretKey()));
        return j;
    }

    private static boolean isServerGenerated(int licensingMethod) {
        return SERVER_GEN == licensingMethod;
    }

    private static boolean isManual(int licensingMethod) {
        return MANUAL == licensingMethod;
    }

    private void writeLicenseKey(String licenseKey) {
        try {
            TextFile secretFile = new TextFile(LICENSE_FILE);
            if(!secretFile.exists()) {
                secretFile.write(licenseKey);
            }
        } catch(IOException e) {
            //ignore?
        }
    }
}
