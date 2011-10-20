package metanectar.model;

import antlr.ANTLRException;
import com.cloudbees.commons.metanectar.provisioning.ComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.DefaultLeaseId;
import com.cloudbees.commons.metanectar.provisioning.FutureComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.LeaseId;
import com.cloudbees.commons.metanectar.provisioning.ProvisioningException;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Util;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.model.labels.LabelExpression;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.DescribableList;
import metanectar.persistence.LeaseState;
import metanectar.persistence.SlaveLeaseTable;
import metanectar.persistence.UIDTable;
import metanectar.provisioning.SharedSlaveRetentionStrategy;
import net.jcip.annotations.GuardedBy;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Represents a shared slave
 *
 * @author Stephen Connolly
 */
public class SharedSlave extends AbstractItem implements TopLevelItem, IdentifiableSlaveManager {
    private static final Logger LOGGER = Logger.getLogger(SharedSlave.class.getName());
    // property state

    protected volatile DescribableList<SharedSlaveProperty<?>, SharedSlavePropertyDescriptor> properties =
            new PropertyList(this);
    private String remoteFS;
    private int numExecutors;
    private Node.Mode mode = Node.Mode.NORMAL;
    private String labelString;
    private RetentionStrategy<? extends Computer> retentionStrategy = new CloudRetentionStrategy(1);
    private List<? extends NodeProperty<?>> nodeProperties = Collections.emptyList();
    private ComputerLauncher launcher;

    private String uid;

    protected SharedSlave(ItemGroup parent, String name) {
        super(parent, name);
        uid = UIDTable.generate();
    }

    /**
     * Called when object has been deserialized from a stream.
     *
     * @return {@code this}, or a replacement for {@code this}.
     * @throws java.io.ObjectStreamException if the object cannot be restored.
     * @see <a href="http://download.oracle.com/javase/1.3/docs/guide/serialization/spec/input.doc6.html">The Java
     * Object Serialization Specification</a>
     */
    private Object readResolve() throws ObjectStreamException {
        if (uid == null) {
            uid = UIDTable.generate();
        }
        return this;
    }

    public boolean isBuilding() {
        Set<String> leases = SlaveLeaseTable.getLeases(getUid(), LeaseState.LEASED);
        return leases == null || !leases.isEmpty();
    }

    public String getUid() {
        return uid;
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
        return Util.fixNull(labelString).trim();
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
        return isBuilding() ? "slave-computer-w.png" : "slave-computer.png";
    }

    public StatusIcon getIconColor() {
        return new StockStatusIcon(getIcon(), Messages._JenkinsServer_Status_Online());
    }

    //////// Properties

    public DescribableList<SharedSlaveProperty<?>, SharedSlavePropertyDescriptor> getProperties() {
        return properties;
    }

    public void setProperties(DescribableList<SharedSlaveProperty<?>, SharedSlavePropertyDescriptor> properties) {
        this.properties = properties;
    }

    public List<Action> getPropertyActions() {
        ArrayList<Action> result = new ArrayList<hudson.model.Action>();
        for (SharedSlaveProperty<?> prop : properties) {
            result.addAll(prop.getSlaveActions(this));
        }
        return result;
    }

    @Override
    public List<Action> getActions() {
        List<Action> result = new ArrayList<Action>(super.getActions());
        result.addAll(getPropertyActions());
        return Collections.unmodifiableList(result);
    }

    @Override
    public void addAction(Action a) {
        if(a==null) throw new IllegalArgumentException();
        super.getActions().add(a);
    }

    //////// Action methods

    public synchronized void doConfigSubmit(StaplerRequest req,
                                            StaplerResponse rsp)
            throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");
        try {
            JSONObject json = req.getSubmittedForm();

            req.bindJSON(this, json.getJSONObject("node"));

            properties.rebuild(req, json.optJSONObject("properties"), SharedSlavePropertyDescriptor.all());

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
    public synchronized void doSubmitDescription(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
        checkPermission(CONFIGURE);

        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Deletes this item.
     */
    @CLIMethod(name = "shared-slave-delete")
    public void doDoDelete(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, InterruptedException {
        requirePOST();
        delete();
        if (rsp != null) // null for CLI
        {
            rsp.sendRedirect2(req.getContextPath() + "/" + getParent().getUrl());
        }
    }

    public void delete(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        try {
            doDoDelete(req, rsp);
        } catch (InterruptedException e) {
            // TODO: allow this in Stapler
            throw new ServletException(e);
        }
    }

    @CLIMethod(name = "shared-slave-force-release")
    public void doForceRelease(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, InterruptedException {
        SlaveLeaseTable.dropOwner(getUid());
        if (rsp != null) // null for CLI
        {
            rsp.sendRedirect(".");  // go to the top page
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
        SlaveLeaseTable.dropOwner(getUid());
        UIDTable.drop(getUid());
    }

    public ComputerLauncherFactory newComputerLauncherFactory(LeaseId leaseId) {
        return new SharedNodeComputerLauncherFactory(leaseId, getName(), getDescription(), getNumExecutors(),
                getLabelString(), getRemoteFS(), getMode(), new SharedSlaveRetentionStrategy(), getNodeProperties(),
                getLauncher());
    }

    public boolean canProvision(String labelExpression) {
        Set<String> leases = SlaveLeaseTable.getLeases(getUid());
        if (leases != null && !leases.isEmpty()) {
            // TODO when we have a more advanced slave launch mechanism we can give split leases.
            return false;
        }
        try {
            if (labelExpression == null) {
                return Node.Mode.NORMAL.equals(getMode());
            }
            Label label = LabelExpression.parseExpression(labelExpression);
            return label.matches(Label.parse(getLabelString()));
        } catch (ANTLRException e) {
            // if we don't understand the label expression we cannot provision it (might be a new syntax... our
            // problem)
            return false;
        }
    }

    public Collection<String> getLabels() {
        HashSet<String> result = new HashSet<String>();
        for (LabelAtom label : Label.parse(getLabelString())) {
            result.add(label.getName());
        }
        return result;
    }

    public FutureComputerLauncherFactory provision(String labelExpression, TaskListener listener, int numOfExecutors)
            throws ProvisioningException {
        final String leaseUid = UUID.randomUUID().toString();
        if (!SlaveLeaseTable.registerRequest(getUid(), leaseUid)) {
            throw new ProvisioningException("Could not register lease request");
        }
        Set<String> leases = SlaveLeaseTable.getLeases(getUid());
        if (leases == null) {
            SlaveLeaseTable.updateState(leaseUid, LeaseState.REQUESTED, LeaseState.DECOMMISSIONED);
            SlaveLeaseTable.decommissionLease(leaseUid);
            throw new ProvisioningException("Could not confirm lease request");
        }
        if (!Collections.singleton(leaseUid).equals(leases)) {
            SlaveLeaseTable.updateState(leaseUid, LeaseState.REQUESTED, LeaseState.DECOMMISSIONED);
            SlaveLeaseTable.decommissionLease(leaseUid);
            // TODO add some optimistic retry for this race condition (we'll get a retry anyway)
            throw new ProvisioningException("Could not provision node");
        }
        return new FutureComputerLauncherFactory(getName(), numOfExecutors,
                Computer.threadPoolForRemoting.submit(new Callable<ComputerLauncherFactory>() {
                    public ComputerLauncherFactory call() throws Exception {
                        if (!LeaseState.REQUESTED.equals(SlaveLeaseTable.getStatus(leaseUid))) {
                            // should never happen
                            throw new ProvisioningException("Illegal state of provisioning request");
                        }
                        if (!SlaveLeaseTable.updateState(leaseUid, LeaseState.REQUESTED, LeaseState.PLANNED)) {
                            SlaveLeaseTable.updateState(leaseUid, LeaseState.REQUESTED, LeaseState.DECOMMISSIONED);
                            SlaveLeaseTable.decommissionLease(leaseUid);
                            throw new ProvisioningException("Could not update lease status");
                        }
                        if (!SlaveLeaseTable.updateState(leaseUid, LeaseState.PLANNED, LeaseState.AVAILABLE)) {
                            SlaveLeaseTable.updateState(leaseUid, LeaseState.PLANNED, LeaseState.DECOMMISSIONED);
                            SlaveLeaseTable.decommissionLease(leaseUid);
                            throw new ProvisioningException("Could not update lease status");
                        }
                        ComputerLauncherFactory launcherFactory =
                                newComputerLauncherFactory(new DefaultLeaseId(leaseUid));
                        if (!SlaveLeaseTable.registerLease(leaseUid, "TODO")) {
                            // TODO get tenant id
                            SlaveLeaseTable.updateState(leaseUid, LeaseState.AVAILABLE, LeaseState.DECOMMISSIONING);
                            SlaveLeaseTable.updateState(leaseUid, LeaseState.DECOMMISSIONING, LeaseState.DECOMMISSIONED);
                            SlaveLeaseTable.decommissionLease(leaseUid);
                            throw new ProvisioningException("Could not confirm lease");
                        }
                        return launcherFactory;
                    }
                }));
    }

    public void release(ComputerLauncherFactory allocatedSlave) {
        LeaseId leaseId = allocatedSlave.getLeaseId();
        if (leaseId instanceof DefaultLeaseId) {
            String leaseUid = ((DefaultLeaseId) leaseId).getUuid();
            if (getUid().equals(SlaveLeaseTable.getOwner(leaseUid))) {
                LeaseState status = SlaveLeaseTable.getStatus(leaseUid);
                if (status == null) {
                    LOGGER.log(Level.INFO, "SharedSlave[{0}] could not record return of lease: {1}",
                            new Object[]{getUrl(), leaseUid});
                    return;
                }
                switch (status) {
                    case REQUESTED:
                        SlaveLeaseTable.updateState(leaseUid, LeaseState.REQUESTED, LeaseState.DECOMMISSIONED);
                        SlaveLeaseTable.decommissionLease(leaseUid);
                        break;
                    case PLANNED:
                        SlaveLeaseTable.updateState(leaseUid, LeaseState.PLANNED, LeaseState.DECOMMISSIONED);
                        SlaveLeaseTable.decommissionLease(leaseUid);
                        break;
                    case AVAILABLE:
                        SlaveLeaseTable.updateState(leaseUid, LeaseState.AVAILABLE, LeaseState.DECOMMISSIONING);
                        SlaveLeaseTable.updateState(leaseUid, LeaseState.DECOMMISSIONING, LeaseState.DECOMMISSIONED);
                        SlaveLeaseTable.decommissionLease(leaseUid);
                        break;
                    case LEASED:
                        SlaveLeaseTable.returnLease(leaseUid, SlaveLeaseTable.getTenant(leaseUid));
                    case RETURNED:
                        SlaveLeaseTable.updateState(leaseUid, LeaseState.RETURNED, LeaseState.DECOMMISSIONING);
                    case DECOMMISSIONING:
                        SlaveLeaseTable.updateState(leaseUid, LeaseState.DECOMMISSIONING, LeaseState.DECOMMISSIONED);
                    case DECOMMISSIONED:
                        SlaveLeaseTable.decommissionLease(leaseUid);
                        LOGGER.log(Level.INFO, "SharedSlave[{0}] returned from lease {1}",
                                new Object[]{getUrl(), leaseUid});
                        break;
                    default:
                        break;
                }
            } else {
                LOGGER.log(Level.INFO, "SharedSlave[{0}] returned an unknown lease: {1}",
                        new Object[]{getUrl(), leaseUid});
            }
        } else {
            LOGGER.log(Level.INFO, "SharedSlave[{0}] returned an unknown lease: {1}", new Object[]{getUrl(), leaseId});
        }
    }

    public boolean isProvisioned(LeaseId id) {
        if (id instanceof DefaultLeaseId) {
            Set<String> leases = SlaveLeaseTable.getLeases(getUid());
            return leases != null && leases.contains(((DefaultLeaseId) id).getUuid());
        }
        return false;
    }

    public LeaseId getLeaseId() {
        Set<String> leases = SlaveLeaseTable.getLeases(getUid());
        return leases == null || leases.isEmpty() ? null : new DefaultLeaseId(leases.iterator().next());
    }

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.SharedSlave_SlaveResource_DisplayName();
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            // TODO how to check for create permission?
            return new SharedSlave(parent, name);
        }

        public List<NodePropertyDescriptor> getNodePropertyDescriptors() {
            return Collections.emptyList();
        }

        public List<SharedSlavePropertyDescriptor> getSlavePropertyDescriptors() {
            return SharedSlavePropertyDescriptor.all();
        }

        public List<Descriptor<ComputerLauncher>> getComputerLauncherDescriptors() {
            DescriptorExtensionList<ComputerLauncher, Descriptor<ComputerLauncher>> unfiltered =
                    Hudson.getInstance().getDescriptorList(ComputerLauncher.class);
            return Lists.newArrayList(Iterables.filter(unfiltered,
                    SharedSlaveComputerLauncherPredicate.getInstance()));
        }

    }

    public static class PropertyList extends DescribableList<SharedSlaveProperty<?>, SharedSlavePropertyDescriptor> {
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
            if (owner instanceof SharedSlave) {
                for (SharedSlaveProperty p : this) {
                    p.setOwner(getOwner());
                }
            }
        }
    }

    @CLIResolver
    public static SharedSlave resolveForCLI(
            @Argument(required=true, metaVar="NAME", usage="Shared slave name") String name) throws CmdLineException {
        SharedSlave sharedSlave = MetaNectar.getInstance().getItemByFullName(name, SharedSlave.class);
        if (sharedSlave == null)
            throw new CmdLineException(null,"No such shared slave exists: " + name);
        return sharedSlave;
    }

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(SharedSlave.class, Messages._SharedSlave_PermissionsTitle());

    // TODO
    // CREATE and DELETE are disabled until Jenkins core is modified to support finer-grained permission checking
    // for creation and deletion of items
    /*
    public static final Permission CREATE = new Permission(PERMISSIONS,"Create", Messages._SharedSlave_Create_Permission(), Item.CREATE);

    public static final Permission DELETE = new Permission(PERMISSIONS,"Delete", Messages._SharedSlave_Delete_Permission(), Item.DELETE);
    */

    public static final Permission CONFIGURE = new Permission(PERMISSIONS,"Configure", Messages._SharedSlave_Configure_Permission(), Item.CONFIGURE);

}
