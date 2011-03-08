package metanectar.model;

import hudson.Extension;
import hudson.model.*;

import java.util.Collection;
import java.util.Collections;

/**
 * Representation of remote Jenkins server inside Meta Nectar.
 *
 * @author Kohsuke Kawaguchi
 */
public class JenkinsServer extends AbstractItem implements TopLevelItem {
    
    protected JenkinsServer(ItemGroup parent, String name) {
        super(parent, name);
    }

    protected View createInitialView() {
        return new JenkinsServerAllView(hudson.model.Messages.Hudson_ViewName());
    }

    /**
     * No nested job under Jenkins server
     *
     * @deprecated
     *      No one shouldn't be calling this directly.
     */
    @Override
    public final Collection<? extends Job> getAllJobs() {
        return Collections.emptyList();
    }

    public TopLevelItemDescriptor getDescriptor() {
        return (TopLevelItemDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public StatusIcon getIconColor() {
        return new StockStatusIcon("computer.png",Messages._JenkinsServer_Status_Online());
    }


    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return "Jenkins server";
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new JenkinsServer(parent,name);
        }
    }
}
