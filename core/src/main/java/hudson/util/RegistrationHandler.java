package hudson.util;

import hudson.Util;
import hudson.model.Hudson;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import static javax.servlet.http.HttpServletResponse.*;

/** Handles Registration. Could be modified to be discoverable.
 * @author Kedar Mhaswade (km@infradna.com)
 * Date: Jun 19, 2010
 */
public final class RegistrationHandler {

    private volatile boolean registered = false;
    private final ServletContext context;
    private static RegistrationHandler instance;
    private static final File LICENSE_FILE = new File(Hudson.getInstance().getRootDir(),"license.key");

    public synchronized static RegistrationHandler instance(ServletContext context) {
        instance = new RegistrationHandler(context);
        return instance;
    }

    private RegistrationHandler(ServletContext context) {
        this.context = context;
    }

    public boolean isRegistered() {
        boolean d = registered;
        return d;
    }

    public boolean isLicenseKeyValid() {
        return LICENSE_FILE.exists();    //need a better way
    }
    
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        if (!isLicenseKeyValid()) {
            rsp.setStatus(SC_UNAUTHORIZED);
            req.getView(this, "index").forward(req, rsp);
        }
    }
    
    public void doRegister(StaplerRequest request, StaplerResponse response, @QueryParameter String licensingMethod,
                         @QueryParameter String userName, @QueryParameter String password, @QueryParameter String email,
                         @QueryParameter String company, @QueryParameter String subscribe,
                         @QueryParameter String licenseKey) throws IOException, ServletException {
        //user has clicked on register button
        if (isServerGenerated(licensingMethod)) {
            JSONObject j = toRegistrationData(userName, password, email, company, subscribe);
        } else {
            if (!verified(licenseKey)) {
                request.setAttribute("message", "Invalid License Key, try again"); //i18n
                request.getView(this, "index").forward(request, response);
            }
        }
    }

    public boolean verified(String licenseKey) {
        return false; //for now, just make them go the server_generated route
    }

    private JSONObject toRegistrationData(String userName, String password, String email, String company, String subscribe) {
        JSONObject j = new JSONObject();
        j.put("userName", userName);
        j.put("password", password);
        j.put("email", email);
        j.put("company", company);
        j.put("subscribe", subscribe);
        j.put("instance-id", Util.getDigestOf(Hudson.getInstance().getSecretKey()));
        return j;
    }

    private static boolean isServerGenerated(String licensingMethod) {
        return "1".equals(licensingMethod);
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
