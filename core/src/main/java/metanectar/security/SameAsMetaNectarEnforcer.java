package metanectar.security;

import com.cloudbees.commons.metanectar.context.ItemNodeContext;
import hudson.Extension;
import hudson.model.Hudson;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import metanectar.model.ConnectedMaster;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Pushes the exact same setting as MetaNectar to master.
 *
 * @author Kohsuke Kawaguchi
 */
public class SameAsMetaNectarEnforcer extends SecurityEnforcer {
    @DataBoundConstructor
    public SameAsMetaNectarEnforcer() {}

    @Override
    protected void updateNodeContext(ConnectedMaster node, ItemNodeContext nodeContext) {
        SecurityRealm s = Hudson.getInstance().getSecurityRealm();
        if (nodeContext.getAuthenticationRealm() != s)
            nodeContext.setAuthenticationRealm(s);

        AuthorizationStrategy a = Hudson.getInstance().getAuthorizationStrategy();
        if (nodeContext.getAuthorizationStrategy() != a)
            nodeContext.setAuthorizationStrategy(a);
    }

    @Extension
    public static class DescriptorImpl extends SecurityEnforcerDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.SameAsMetaNectarEnforcer_DisplayName();
        }
    }
}
