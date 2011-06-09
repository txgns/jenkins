package metanectar.model;

import hudson.Extension;
import hudson.Util;
import hudson.cli.declarative.CLIMethod;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.HealthReport;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.StatusIcon;
import hudson.model.StockStatusIcon;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.DescribableList;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Represents a slave
 *
 * @author Stephen Connolly
 */
public class SharedSlave extends AbstractItem implements TopLevelItem {
    // property state

    protected volatile DescribableList<SharedSlaveProperty<?>,SharedSlavePropertyDescriptor> properties =
            new PropertyList(this);
    private String remoteFS;
    private int numExecutors;
    private Node.Mode mode = Node.Mode.NORMAL;
    private String labelString;
    private RetentionStrategy<? extends Computer> retentionStrategy = new CloudRetentionStrategy(1);
    private List<? extends NodeProperty<?>> nodeProperties = Collections.emptyList();
    private ComputerLauncher launcher;

    protected SharedSlave(ItemGroup parent, String name) {
        super(parent, name);
    }

    public boolean isBuilding() {
        return false; // TODO
    }

    public ComputerLauncher getLauncher() {
        return launcher;
    }

    public void setLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }

    /**
     * Returns the remote file system path to be used for this node.
     *
     * @return the remote file system path to be used for this node.
     */
    public String getRemoteFS() {
        return remoteFS;
    }

    /**
     * Returns the number of executors supported on this node.
     *
     * @return the number of executors supported on this node.
     */
    public int getNumExecutors() {
        return numExecutors;
    }

    /**
     * Returns the mode of this Node with respect to what types of job can be run on it.
     *
     * @return the mode of this Node with respect to what types of job can be run on it.
     */
    public Node.Mode getMode() {
        return mode;
    }

    /**
     * Returns the label string for this node.
     *
     * @return the label string for this node.
     */
    public String getLabelString() {
        return labelString;
    }

    /**
     * Returns the retention strategy for this node.
     *
     * @return the retention strategy for this node.
     */
    public RetentionStrategy getRetentionStrategy() {
        return retentionStrategy;
    }

    /**
     * Returns the properties of this node.
     *
     * @return the properties of this node.
     */
    public List<? extends NodeProperty<?>> getNodeProperties() {
        return nodeProperties;
    }

    public void setRemoteFS(String remoteFS) {
        this.remoteFS = remoteFS;
    }

    public void setNumExecutors(int numExecutors) {
        this.numExecutors = numExecutors;
    }

    public void setMode(Node.Mode mode) {
        this.mode = mode;
    }

    public void setLabelString(String labelString) {
        this.labelString = labelString;
    }

    public void setRetentionStrategy(RetentionStrategy<? extends Computer> retentionStrategy) {
        this.retentionStrategy = retentionStrategy;
    }

    public void setNodeProperties(List<? extends NodeProperty<?>> nodeProperties) {
        this.nodeProperties = nodeProperties;
    }

//////// AbstractItem

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

    public void setProperties(DescribableList<SharedSlaveProperty<?>, SharedSlavePropertyDescriptor> properties) {
        this.properties = properties;
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
        try {
            JSONObject json = req.getSubmittedForm();

            req.bindJSON(this, json.getJSONObject("node"));

            PropertyList t = new PropertyList(properties.toList());
            t.rebuild(req,json.optJSONObject("properties"), SharedSlavePropertyDescriptor.all());
            properties.clear();
            for (SharedSlaveProperty p : t) {
                p.setOwner(this);
                properties.add(p);
            }

            save();

            String newName = req.getParameter("name");
            if (newName != null && !newName.equals(name)) {
                // check this error early to avoid HTTP response splitting.
                Hudson.checkGoodName(newName);
                rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8"));
            } else {
                rsp.sendRedirect(".");
            }
        } catch (JSONException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("Failed to parse form data. Please report this problem as a bug");
            pw.println("JSON=" + req.getSubmittedForm());
            pw.println();
            e.printStackTrace(pw);

            rsp.setStatus(SC_BAD_REQUEST);
            sendError(sw.toString(), req, rsp, true);
        }
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(CONFIGURE);

        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Deletes this item.
     */
    @CLIMethod(name="delete-job")
    public void doDoDelete( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, InterruptedException {
        requirePOST();
        delete();
        if (rsp != null) // null for CLI
            rsp.sendRedirect2(req.getContextPath()+"/"+getParent().getUrl());
    }

    public void delete( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        try {
            doDoDelete(req,rsp);
        } catch (InterruptedException e) {
            // TODO: allow this in Stapler
            throw new ServletException(e);
        }
    }

    /**
     * Deletes this item.
     */
    public synchronized void delete() throws IOException, InterruptedException {
        checkPermission(DELETE);
        performDelete();

        try {
            invokeOnDeleted();
        } catch (AbstractMethodError e) {
            // ignore
        }
    }

    /**
     * Renames this slave.
     */
    public/* not synchronized. see renameTo() */void doDoRename(
            StaplerRequest req, StaplerResponse rsp) throws IOException,
            ServletException {
        requirePOST();
        // rename is essentially delete followed by a create
        checkPermission(CREATE);
        checkPermission(DELETE);

        String newName = req.getParameter("newName");
        Hudson.checkGoodName(newName);

        if (isBuilding()) {
            // redirect to page explaining that we can't rename now
            rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8"));
            return;
        }

        renameTo(newName);
        // send to the new job page
        // note we can't use getUrl() because that would pick up old name in the
        // Ancestor.getUrl()
        rsp.sendRedirect2(req.getContextPath() + '/' + getParent().getUrl()
                + getShortUrl());
    }

    /**
     * A pointless function to work around what appears to be a HotSpot problem. See HUDSON-5756 and bug 6933067
     * on BugParade for more details.
     */
    private void invokeOnDeleted() throws IOException {
        getParent().onDeleted(this);
    }

    /**
     * Does the real job of deleting the item.
     */
    protected void performDelete() throws IOException, InterruptedException {
        getConfigFile().delete();
        Util.deleteRecursive(getRootDir());
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

        public List<NodePropertyDescriptor> getNodePropertyDescriptors() {
            return Collections.emptyList();

        }

        public List<SharedSlavePropertyDescriptor> getSlavePropertyDescriptors() {
            return SharedSlavePropertyDescriptor.all();
        }
    }

    public static class PropertyList extends DescribableList<SharedSlaveProperty<?>,SharedSlavePropertyDescriptor> {
        private PropertyList(SharedSlave owner) {
            super(owner);
        }

        public PropertyList() {// needed for XStream deserialization
        }

        /*package*/ PropertyList(List<SharedSlaveProperty<?>> initialList) {
            super(NOOP, initialList);
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
