package metanectar.model;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.HealthReport;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.StatusIcon;
import hudson.model.StockStatusIcon;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.util.DescribableList;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Represents a slave
 *
 * @author Stephen Connolly
 */
public class SharedSlave extends AbstractItem implements TopLevelItem {
    // property state

    protected volatile DescribableList<SharedSlaveProperty<?>,SharedSlavePropertyDescriptor> properties =
            new PropertyList(this);

    protected SharedSlave(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    public Collection<? extends Job> getAllJobs() {
        return Collections.emptySet();
    }

    public TopLevelItemDescriptor getDescriptor() {
        return (TopLevelItemDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    @Override
    public String getSearchUrl() {
        return "slave/" + name;
    }

    //////// Methods to handle the weather icon

    /**
     * Get the current health report for a job.
     *
     * @return the health report. Never returns null
     */
    public HealthReport getBuildHealth() {
        List<HealthReport> reports = getBuildHealthReports();
        return reports.isEmpty() ? new HealthReport(100, Messages._SharedSlave_PerfectHealth()) : reports.get(0);
    }

    @Exported(name = "healthReport")
    public List<HealthReport> getBuildHealthReports() {
        return Arrays.asList(new HealthReport(100, Messages._SharedSlave_PerfectHealth()));
    }

    //////// Methods to handle the status icon

    public String getIcon() {
        return "slave-computer.png";
    }

    public StatusIcon getIconColor() {
        return new StockStatusIcon(getIcon(), Messages._JenkinsServer_Status_Online());
    }

    //////// Properties

    public DescribableList<SharedSlaveProperty<?>,SharedSlavePropertyDescriptor> getProperties() {
        return properties;
    }

    public List<hudson.model.Action> getPropertyActions() {
        ArrayList<Action> result = new ArrayList<hudson.model.Action>();
        for (SharedSlaveProperty<?> prop: properties) {
            result.addAll(prop.getSlaveActions(this));
        }
        return result;
    }

    //////// Action methods

    public synchronized void doConfigSubmit(StaplerRequest req,
            StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");
        save();

        rsp.sendRedirect(".");
    }

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.SharedSlave_SlaveResource_DisplayName();
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new SharedSlave(parent, name);
        }
    }

    public static class PropertyList extends DescribableList<SharedSlaveProperty<?>,SharedSlavePropertyDescriptor> {
        private PropertyList(SharedSlave owner) {
            super(owner);
        }

        public PropertyList() {// needed for XStream deserialization
        }

        public SharedSlave getOwner() {
            return (SharedSlave)owner;
        }

        @Override
        protected void onModified() throws IOException {
            for (SharedSlaveProperty p : this)
                p.setOwner(getOwner());
        }
    }

}
