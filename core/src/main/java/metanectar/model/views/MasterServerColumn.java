package metanectar.model.views;

import hudson.Extension;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Paul Sandoz
 */
public class MasterServerColumn extends ListViewColumn {
    @DataBoundConstructor
    public MasterServerColumn() {
    }

    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {
        @Override
        public String getDisplayName() {
            return "Server";
        }
    }

}

