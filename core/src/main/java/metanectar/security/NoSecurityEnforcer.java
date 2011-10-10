package metanectar.security;

import com.cloudbees.commons.metanectar.context.ItemNodeContext;
import hudson.Extension;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import metanectar.model.ConnectedMaster;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link SecurityEnforcer} that doesn't enforce anything.
 * 
 * @author Kohsuke Kawaguchi
 */
public class NoSecurityEnforcer extends SecurityEnforcer {
    @DataBoundConstructor
    public NoSecurityEnforcer() {}

    @Override
    protected void contributeNodeContext(ConnectedMaster node, ItemNodeContext context) {
        context.clearInstance(SecurityRealm.class);
        context.clearInstance(AuthorizationStrategy.class);
    }

    @Extension
    public static class DescriptorImpl extends SecurityEnforcerDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.NoSecurityEnforcer_DisplayName();
        }
    }
}
