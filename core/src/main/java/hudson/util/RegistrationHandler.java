package hudson.util;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

/** Handles Registration. Could be modified to be discoverable.
 * @author Kedar Mhaswade (km@infradna.com)
 * Date: Jun 19, 2010
 */
public final class RegistrationHandler {

    private final List<String> methodNames = new ArrayList<String>();
    private volatile boolean registered = true;
    private final ServletContext context;
    private final String defaultRegistrationMethod;

    private static RegistrationHandler instance;

    public synchronized static RegistrationHandler instance(ServletContext context) {
        instance = new RegistrationHandler(context);
        return instance;
    }

    private RegistrationHandler(ServletContext context) {
        this.context = context;
        methodNames.add("SERVER_GENERATED");
        methodNames.add("MANUAL");
        defaultRegistrationMethod = methodNames.get(0);
        //if (keyFound)
        //registered = true;
    }

    public boolean isRegistered() {
        boolean d = registered;
        return d;
    }

    public List<String> getMethodNames() {
        return methodNames;
    }

    public String getDefaultRegistrationMethod() {
        return defaultRegistrationMethod;
    }
    
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        rsp.setStatus(SC_SERVICE_UNAVAILABLE);
        req.getView(this,"index.jelly").forward(req,rsp);
    }
}
