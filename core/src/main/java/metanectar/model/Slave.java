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
public class Slave extends AbstractItem implements TopLevelItem {
    // property state

    protected volatile DescribableList<SlaveProperty<?>,SlavePropertyDescriptor> properties =
            new PropertyList(this);

    protected Slave(ItemGroup parent, String name) {
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
        return reports.isEmpty() ? new HealthReport(100, Messages._Slave_PerfectHealth()) : reports.get(0);
    }

    @Exported(name = "healthReport")
    public List<HealthReport> getBuildHealthReports() {
        return Arrays.asList(new HealthReport(100, Messages._Slave_PerfectHealth()));
    }

    //////// Methods to handle the status icon

    public String getIcon() {
        return "slave-computer.png";
    }

    public StatusIcon getIconColor() {
        return new StockStatusIcon(getIcon(), Messages._JenkinsServer_Status_Online());
    }

    //////// Properties

    public DescribableList<SlaveProperty<?>,SlavePropertyDescriptor> getProperties() {
        return properties;
    }

    public List<hudson.model.Action> getPropertyActions() {
        ArrayList<Action> result = new ArrayList<hudson.model.Action>();
        for (SlaveProperty<?> prop: properties) {
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
            return Messages.Slave_SlaveResource_DisplayName();
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new Slave(parent, name);
        }
    }

    public static class PropertyList extends DescribableList<SlaveProperty<?>,SlavePropertyDescriptor> {
        private PropertyList(Slave owner) {
            super(owner);
        }

        public PropertyList() {// needed for XStream deserialization
        }

        public Slave getOwner() {
            return (Slave)owner;
        }

        @Override
        protected void onModified() throws IOException {
            for (SlaveProperty p : this)
                p.setOwner(getOwner());
        }
    }

}
