package metanectar.security;

import com.cloudbees.commons.metanectar.context.ItemNodeContext;
import hudson.Extension;
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
    protected void updateNodeContext(ConnectedMaster node, ItemNodeContext context) {
        // no enforcement
    }

    @Extension
    public static class DescriptorImpl extends SecurityEnforcerDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.NoSecurityEnforcer_DisplayName();
        }
    }
}
