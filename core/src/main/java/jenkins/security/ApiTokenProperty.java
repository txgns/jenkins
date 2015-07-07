/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.security;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor.FormException;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.util.HttpResponses;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.security.SecureRandom;
import javax.annotation.Nonnull;
import jenkins.model.IdStrategy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Remembers the API token for this user, that can be used like a password to login.
 *
 *
 * @author Kohsuke Kawaguchi
 * @see ApiTokenFilter
 * @since 1.426
 */
public class ApiTokenProperty extends UserProperty {
    private volatile Secret apiToken;

    /**
     * Enables hiding API tokens from Jenkins admins.
     * Disabled by default in order to retain the behavior.
     * @since TODO
     */
    private static boolean HIDE_TOKEN_FROM_ADMINS = 
            Boolean.getBoolean(ApiTokenProperty.class.getName() + ".hideTokenFromAdmins");
    
    
    @DataBoundConstructor
    public ApiTokenProperty() {
        _changeApiToken();
    }

    /**
     * We don't let the external code set the API token,
     * but for the initial value of the token we need to compute the seed by ourselves.
     */
    /*package*/ ApiTokenProperty(String seed) {
        apiToken = Secret.fromString(seed);
    }

    /**
     * Gets the API token (secured implementation).
     * @return API token or &quot;hidden&quot; if it cannot be retrieved due to any reason.
     */
    @Restricted(NoExternalUse.class)
    public @Nonnull String getSecuredApiToken() {
        return hasPermissionToSeeToken() ? getApiToken() : Messages.ApiTokenProperty_ChangeToken_TokenIsHidden();
    }
    
    /**
     * Gets the API token.
     * Warning! this method does not check for permissions. Anyone can use it 
     * from any Jenkins plugin or Groovy script
     * @return API Token. Never null
     */
    public String getApiToken() {
        String p = apiToken.getPlainText();
        if (p.equals(Util.getDigestOf(Jenkins.getInstance().getSecretKey()+":"+user.getId()))) {
            // if the current token is the initial value created by pre SECURITY-49 Jenkins, we can't use that.
            // force using the newer value
            apiToken = Secret.fromString(p=API_KEY_SEED.mac(user.getId()));
        }
        return Util.getDigestOf(p);
    }

    public boolean matchesPassword(String password) {
        return getApiToken().equals(password);
    }
    
    @Restricted(NoExternalUse.class)
    public boolean hasPermissionToSeeToken() {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return false; // Should not happen - we don't display UIs in this stage
        }
        
        // Administrators can do whatever they want
        if (!HIDE_TOKEN_FROM_ADMINS && jenkins.hasPermission(Jenkins.ADMINISTER)) {
            return true;
        }
        
        final User current = User.current();
        if (current == null) {
            return false;
        }
             
        // TODO: If impersonated as System?
        return User.idStrategy().equals(user.getId(), current.getId());
    }

    public void changeApiToken() throws IOException {
        user.checkPermission(Jenkins.ADMINISTER);
        _changeApiToken();
        if (user!=null)
            user.save();
    }

    private void _changeApiToken() {
        byte[] random = new byte[16];   // 16x8=128bit worth of randomness, since we use md5 digest as the API token
        RANDOM.nextBytes(random);
        apiToken = Secret.fromString(Util.toHexString(random));
    }

    @Override
    public UserProperty reconfigure(StaplerRequest req, JSONObject form) throws FormException {
        return this;
    }

    @Extension
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        public String getDisplayName() {
            return Messages.ApiTokenProperty_DisplayName();
        }

        /**
         * When we are creating a default {@link ApiTokenProperty} for User,
         * we need to make sure it yields the same value for the same user,
         * because there's no guarantee that the property is saved.
         *
         * But we also need to make sure that an attacker won't be able to guess
         * the initial API token value. So we take the seed by hashing the secret + user ID.
         */
        public ApiTokenProperty newInstance(User user) {
            return new ApiTokenProperty(API_KEY_SEED.mac(user.getId()));
        }

        public HttpResponse doChangeToken(@AncestorInPath User u, StaplerResponse rsp) throws IOException {
            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p==null) {
                p = newInstance(u);
                u.addProperty(p);
            } else {
                p.changeApiToken();
            }
            rsp.setHeader("script","document.getElementById('apiToken').value='"+p.getSecuredApiToken()+"'");
            return HttpResponses.html(p.hasPermissionToSeeToken() 
                    ? Messages.ApiTokenProperty_ChangeToken_Success() 
                    : Messages.ApiTokenProperty_ChangeToken_SuccessHidden());
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * We don't want an API key that's too long, so cut the length to 16 (which produces 32-letter MAC code in hexdump)
     */
    private static final HMACConfidentialKey API_KEY_SEED = new HMACConfidentialKey(ApiTokenProperty.class,"seed",16);
}
