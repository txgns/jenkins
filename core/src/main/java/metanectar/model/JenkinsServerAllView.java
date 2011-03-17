package metanectar.model;

import hudson.Extension;
import hudson.model.*;
import hudson.model.Messages;
import hudson.views.ListViewColumn;
import hudson.views.StatusColumn;
import metanectar.model.views.JenkinsServerColumn;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * This is a duplicate of {@link }hudson.model.AllView} that extends from
 * {@link JenkinsServerView} such that jelly templates can be shared.
 *
 * @author Paul Sandoz
 */
public class JenkinsServerAllView extends JenkinsServerView {
    @DataBoundConstructor
    public JenkinsServerAllView(String name) {
        super(name);
    }

    public JenkinsServerAllView(String name, ViewGroup owner) {
        this(name);
        this.owner = owner;
    }

    public Iterable<ListViewColumn> getColumns() {
        return Arrays.asList(
                new StatusColumn(),
                new JenkinsServerColumn());
    }

    @Override
    public String getDescription() {
        return Hudson.getInstance().getDescription();
    }

    @Override
    public boolean isEditable() {
        return false;
    }

    @Override
    public boolean contains(TopLevelItem item) {
        return true;
    }

    @Override
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
        return Hudson.getInstance().doCreateItem(req, rsp);
    }

    @Override
    public Collection<TopLevelItem> getItems() {
        return Hudson.getInstance().getItems();
    }

    @Override
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(Hudson.ADMINISTER);

        Hudson.getInstance().setSystemMessage(req.getParameter("description"));
        rsp.sendRedirect(".");
    }

    @Override
    public String getPostConstructLandingPage() {
        return ""; // there's no configuration page
    }

    @Override
    public void onJobRenamed(Item item, String oldName, String newName) {
        // noop
    }

    @Override
    protected void submit(StaplerRequest req) throws IOException, ServletException, Descriptor.FormException {
        // noop
    }

    @Extension
    public static final class DescriptorImpl extends ViewDescriptor {
        @Override
        public boolean isInstantiable() {
            for (View v : Stapler.getCurrentRequest().findAncestorObject(ViewGroup.class).getViews())
                if(v instanceof AllView)
                    return false;
            return true;
        }

        public String getDisplayName() {
            return Messages.Hudson_ViewName();
        }
    }

}

