package hudson.license;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.DescriptorByNameOwner;
import hudson.model.Hudson;
import hudson.util.FormValidation;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Handles Registration. Could be modified to be discoverable. Note that this has to behave like Hudson instance and
 * hence special care needs to be taken.
 *
 * @author Kedar Mhaswade (km@infradna.com)
 *         Date: Jun 19, 2010
 */
public final class RegistrationHandler extends AbstractDescribableImpl<RegistrationHandler> implements DescriptorByNameOwner {
    private final ServletContext context;

    private RegistrationData data = new RegistrationData();

    public RegistrationHandler(ServletContext context) {
        this.context = context;
    }

    public RegistrationData getRegistration() {
        return data;
    }

    public void doDone(StaplerRequest request, StaplerResponse response) throws IOException, ServletException, GeneralSecurityException {
        ServerResponse rsp = request.bindJSON(ServerResponse.class, request.getSubmittedForm());
        if (rsp.isValid()) {
            context.setAttribute("app", Hudson.getInstance());
            rsp.save();
        }
        request.getView(rsp,"index").forward(request,response);
    }

    public HttpResponse doDynamic() throws IOException, ServletException, InterruptedException {
        return HttpResponses.redirectViaContextPath("/registration/");
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

    private static boolean validEmail(String email) {
        if (email == null || email.indexOf('@') < 0)
            return false;
        return true;
    }
    @Extension
    public static class DescriptorImpl extends Descriptor<RegistrationHandler> {
        @Override
        public String getDisplayName() {
            return ""; // not used
        }
        public FormValidation doCheckEmail(@QueryParameter(fixEmpty = true) String email) {
            if (!validEmail(email))
                return FormValidation.error("Provide a valid email");
            return FormValidation.ok();
        }

        public FormValidation doCheckPassword(@QueryParameter(fixEmpty = true) String password) {
            if (password == null)
                return FormValidation.error("Provide a Password");
            return FormValidation.ok();
        }

        public FormValidation doCheckCompany(@QueryParameter(fixEmpty = true) String company) {
            if (company == null)
                return FormValidation.error("Provide a company name");
            return FormValidation.ok();
        }
    }
}