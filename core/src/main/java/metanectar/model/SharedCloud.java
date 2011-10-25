package metanectar.model;

import antlr.ANTLRException;
import com.cloudbees.commons.metanectar.provisioning.ComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.DefaultLeaseId;
import com.cloudbees.commons.metanectar.provisioning.FutureComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.LeaseId;
import com.cloudbees.commons.metanectar.provisioning.ProvisioningException;
import com.cloudbees.commons.nectar.nodeiterator.NodeIterator;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Util;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.model.*;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.NodeProvisioner;
import hudson.util.DescribableList;
import metanectar.persistence.LeaseRecord;
import metanectar.persistence.LeaseState;
import metanectar.persistence.SlaveLeaseListener;
import metanectar.persistence.SlaveLeaseTable;
import metanectar.persistence.UIDTable;
import metanectar.provisioning.SharedSlaveRetentionStrategy;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static metanectar.persistence.LeaseState.AVAILABLE;
import static metanectar.persistence.LeaseState.DECOMMISSIONED;
import static metanectar.persistence.LeaseState.DECOMMISSIONING;
import static metanectar.persistence.LeaseState.LEASED;
import static metanectar.persistence.LeaseState.PLANNED;
import static metanectar.persistence.LeaseState.REQUESTED;
import static metanectar.persistence.LeaseState.RETURNED;
import static metanectar.persistence.SlaveLeaseTable.decommissionLease;
import static metanectar.persistence.SlaveLeaseTable.dropOwner;
import static metanectar.persistence.SlaveLeaseTable.getLeaseRecords;
import static metanectar.persistence.SlaveLeaseTable.getLeases;
import static metanectar.persistence.SlaveLeaseTable.getOwner;
import static metanectar.persistence.SlaveLeaseTable.getStatus;
import static metanectar.persistence.SlaveLeaseTable.getTenant;
import static metanectar.persistence.SlaveLeaseTable.planResource;
import static metanectar.persistence.SlaveLeaseTable.registerLease;
import static metanectar.persistence.SlaveLeaseTable.registerRequest;
import static metanectar.persistence.SlaveLeaseTable.returnLease;
import static metanectar.persistence.SlaveLeaseTable.updateState;
import static metanectar.persistence.SlaveLeaseTable.updateStateAndResource;

/**
 * Represents a shared cloud
 *
 * @author Stephen Connolly
 */
public class SharedCloud extends AbstractItem implements TopLevelItem, SlaveTrader {
    private static final Logger LOGGER = Logger.getLogger(SharedCloud.class.getName());
    // property state

    protected volatile DescribableList<SharedCloudProperty<?>, SharedCloudPropertyDescriptor> properties =
            new PropertyList(this);

    private transient ConcurrentMap<String, NodeResource> nodeResources = new ConcurrentHashMap<String, NodeResource>();

    private boolean reuseNodes;

    private int reuseTimeout;

    private Cloud cloud;

    private String uid;

    private boolean disabled = true;

    protected SharedCloud(ItemGroup parent, String name) {
        super(parent, name);
        uid = UIDTable.generate();
    }

    private Object readResolve() {
        if (nodeResources == null) {
            nodeResources = new ConcurrentHashMap<String, NodeResource>();
        }
        if (!reuseNodes) {
            reuseTimeout = 1;
        }
        if (uid == null) {
            uid = UIDTable.generate();
        }
        return this;
    }

    public boolean isEnabled() {
        return !disabled;
    }

    public boolean isQuiet() {
        Set<String> leases = getLeases(getUid());
        return disabled && (leases == null || leases.isEmpty());
    }

    public boolean isBuilding() {
        Set<String> leases = getLeases(getUid(), LEASED);
        return leases == null || !leases.isEmpty();
    }

    public boolean isReuseNodes() {
        return reuseNodes;
    }

    public int getReuseTimeout() {
        return reuseTimeout;
    }

    public Cloud getCloud() {
        return cloud;
    }

    public void setCloud(Cloud cloud) {
        this.cloud = cloud;
    }

    public String getUid() {
        return uid;
    }

    public Map<LeaseRecord, ConnectedMaster> getLeaseDetails() {
        Map<LeaseRecord, ConnectedMaster> result = new LinkedHashMap<LeaseRecord, ConnectedMaster>();
        Set<LeaseRecord> leases = getLeaseRecords(getUid());
        if (leases != null) {
            for (LeaseRecord lease : leases) {
                final String tenantId = lease.getTenantId();
                if (tenantId != null) {
                    ConnectedMaster tenant = ConnectedMaster.findByUid(tenantId);
                    if (tenant != null) {
                        result.put(lease, tenant);
                    } else {
                        result.put(lease, null);
                    }
                } else {
                    result.put(lease, null);
                }
            }
        }
        return result;
    }

//////// AbstractItem

    @Override
    public void onCopiedFrom(Item src) {
        try {
            performDelete();
            getParent().onDeleted(this);
            Hudson.getInstance().rebuildDependencyGraph();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot revert copied state of the copied managed cloud: " + this.toString(), e);
        }
        throw new IllegalStateException("Managed clouds cannot be copied");
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
        return "cloud/" + name;
    }

    //////// Methods to handle the weather icon

    /**
     * Get the current health report for a job.
     *
     * @return the health report. Never returns null
     */
    public HealthReport getBuildHealth() {
        List<HealthReport> reports = getBuildHealthReports();
        return reports.isEmpty() ? new HealthReport(100, Messages._SharedCloud_PerfectHealth()) : reports.get(0);
    }

    @Exported(name = "healthReport")
    public List<HealthReport> getBuildHealthReports() {
        List<HealthReport> result = new ArrayList<HealthReport>();
        if (cloud instanceof AbstractCloudImpl) {
            int instanceCap = ((AbstractCloudImpl) cloud).getInstanceCap();
            if (instanceCap < Integer.MAX_VALUE) {
                Set<String> leases = getLeases(getUid());
                int instanceCount = leases == null ? 0 : leases.size();
                result.add(new HealthReport(100 - (instanceCount * 100 / instanceCap),
                        Messages._SharedCloud_ActiveInstanceCountWithRespectToCap(instanceCount,
                                instanceCap)));
            }
        }
        result.add(new HealthReport(100, Messages._SharedCloud_PerfectHealth()));
        return result;
    }

    //////// Methods to handle the status icon

    public String getIcon() {
        return isBuilding() ? "slave-cloud-w.png" : "slave-cloud.png";
    }

    public StatusIcon getIconColor() {
        return new StockStatusIcon(getIcon(), Messages._JenkinsServer_Status_Online());
    }

    //////// Properties

    public DescribableList<SharedCloudProperty<?>, SharedCloudPropertyDescriptor> getProperties() {
        return properties;
    }

    public void setProperties(DescribableList<SharedCloudProperty<?>, SharedCloudPropertyDescriptor> properties) {
        this.properties = properties;
    }

    public List<Action> getPropertyActions() {
        ArrayList<Action> result = new ArrayList<Action>();
        for (SharedCloudProperty<?> prop : properties) {
            result.addAll(prop.getCloudActions(this));
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
        if (a == null) {
            throw new IllegalArgumentException();
        }
        super.getActions().add(a);
    }

    //////// Action methods

    public synchronized void doEnable(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);
        requirePOST();
        disabled = false;
        if (rsp != null) // null for CLI
        {
            rsp.sendRedirect2(".");
        }
    }

    public synchronized void doDisable(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);
        requirePOST();
        disabled = true;
        if (rsp != null) // null for CLI
        {
            rsp.sendRedirect2(".");
        }
    }

    public synchronized void doConfigSubmit(StaplerRequest req,
                                            StaplerResponse rsp)
            throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);
        requireDisabled();

        description = req.getParameter("description");
        try {
            JSONObject json = req.getSubmittedForm();

            req.bindJSON(this, json.getJSONObject("node"));

            JSONObject cloudJson = json.getJSONObject("cloud");
            cloudJson.element("name", name);
            if (cloud != null && cloud instanceof ReconfigurableDescribable
                    && cloud.getClass().getName().equals(cloudJson.getString("stapler-class"))) {
                ((ReconfigurableDescribable) cloud).reconfigure(req, cloudJson);
            } else {
                cloud = req.bindJSON(Cloud.class, cloudJson);
            }

            if (json.has("reuseNodes")) {
                reuseNodes = true;

                JSONObject j_reuseNodes = json.getJSONObject("reuseNodes");
                reuseTimeout = j_reuseNodes.getInt("reuseTimeout");
            } else {
                reuseNodes = false;
                reuseTimeout = 1;
            }

            properties.rebuild(req, json.optJSONObject("properties"), SharedCloudPropertyDescriptor.all());

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
    @CLIMethod(name = "shared-cloud-delete")
    public void doDoDelete(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, InterruptedException {
        requirePOST();
        requireDisabled();
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

    @CLIMethod(name = "shared-cloud-force-release")
    public void doForceRelease(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, InterruptedException {
        checkPermission(Hudson.ADMINISTER);

        // TODO implement
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
        requireDisabled();
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

    public ComputerLauncherFactory newComputerLauncherFactory(String leaseId) {
        Node node = getNode(leaseId);
        if (node instanceof Slave) {
            final Slave slave = (Slave) node;
            return new SharedNodeComputerLauncherFactory(new DefaultLeaseId(leaseId), node.getNodeName(),
                    node.getNodeDescription(), node.getNumExecutors(), node.getLabelString(), slave.getRemoteFS(),
                    node.getMode(), new SharedSlaveRetentionStrategy(), node.getNodeProperties().toList(),
                    slave.getLauncher());
        } else {
            return null;
        }
    }

    public NodeResource getNodeResource(String leaseId) {
        while (true) {
            NodeResource nodeResource = nodeResources.get(leaseId);
            if (nodeResource != null) {
                return nodeResource;
            }
            byte[] bytes = SlaveLeaseTable.getResource(leaseId);
            if (bytes == null) {
                return null;
            }
            try {
                nodeResource = NodeResource.fromByteArray(bytes);
                nodeResources.putIfAbsent(leaseId, nodeResource);
            } catch (Throwable t) {
                return null; // un-deserializable
            }
        }
    }

    public Node getNode(String leaseId) {
        NodeResource nodeResource = getNodeResource(leaseId);
        return nodeResource == null ? null : nodeResource.getNode();
    }

    public boolean canProvision(String labelExpression) {
        if (disabled) {
            return false;
        }
        final Label label;
        try {
            label = labelExpression == null ? null : Label.parseExpression(labelExpression);
        } catch (ANTLRException e) {
            // if we don't understand the label expression we cannot provision it (might be a new syntax... our
            // problem)
            return false;
        }

        if (reuseNodes) {
            Set<String> returnedLeaseIds = SlaveLeaseTable.getLeases(getUid(), LeaseState.RETURNED);
            if (returnedLeaseIds != null) {
                for (String leaseId : returnedLeaseIds) {
                    Node node = getNode(leaseId);
                    if (node != null) {
                        if (label == null
                                ? !Node.Mode.EXCLUSIVE.equals(node.getMode())
                                : label.matches(node.getAssignedLabels())) {
                            return true;
                        }
                    }
                }
            }
        }

        synchronized (this) {
            try {
                return cloud.canProvision(label);
            } catch (NullPointerException e) {
                LogRecord r = new LogRecord(Level.WARNING,
                        "{0} does not comply with the contract for canProvision(null)");
                r.setParameters(new Object[]{cloud.getClass()});
                r.setThrown(e);
                LOGGER.log(r);
                // if the cloud impl does not get that a null label implies no assigned label, then we cannot provision
                // a slave with no label
                return false;
            }
        }
    }

    public Collection<String> getLabels() {
        return Collections.emptySet(); // we cannot query this info out of Cloud
    }

    public FutureComputerLauncherFactory provision(final String tenant, String labelExpression, TaskListener listener, int numOfExecutors)
            throws ProvisioningException {
        if (disabled) {
            throw new ProvisioningException("Disabled");
        }
        final Label label;
        try {
            label = labelExpression == null ? null : Label.parseExpression(labelExpression);
        } catch (ANTLRException e) {
            throw new ProvisioningException(e.getMessage(), e);
        }
        String reuseLeaseId = null;
        if (reuseNodes) {
            // check if one of the returned nodes matches and see if we can take it
            Set<String> returnedLeaseIds = SlaveLeaseTable.getLeases(getUid(), LeaseState.RETURNED);
            if (returnedLeaseIds != null) {
                for (String leaseId : returnedLeaseIds) {
                    Node node = getNode(leaseId);
                    if (node != null) {
                        if (label == null
                                ? !Node.Mode.EXCLUSIVE.equals(node.getMode())
                                : label.matches(node.getAssignedLabels())) {
                            if (updateState(leaseId, RETURNED, AVAILABLE)) {
                                // we have claimed it
                                reuseLeaseId = leaseId;
                                break;
                            }
                        }
                    }
                }
            }
        }
        final String leaseUid;
        if (reuseLeaseId == null) {
            // provision a new request
            leaseUid = UUID.randomUUID().toString();
            if (!registerRequest(getUid(), leaseUid)) {
                throw new ProvisioningException("Could not register lease request");
            }
        } else {
            leaseUid = reuseLeaseId;
        }
        return new FutureComputerLauncherFactory(getName(), numOfExecutors,
                Computer.threadPoolForRemoting.submit(new Callable<ComputerLauncherFactory>() {
                    public ComputerLauncherFactory call() throws Exception {
                        int excessWorkload = 1; // TODO allow larger workloads

                        LeaseState lastStatus = null;
                        while (true) {
                            LeaseState status = getStatus(leaseUid);
                            if (status == null) {
                                throw new ProvisioningException("Could not provision node");
                            }
                            NodeResource nodeResource = getNodeResource(leaseUid);
                            switch (status) {
                                case REQUESTED:
                                    if (nodeResource == null) {
                                        Collection<NodeProvisioner.PlannedNode> provision =
                                                cloud.provision(label, excessWorkload);
                                        for (NodeProvisioner.PlannedNode plannedNode : provision) {
                                            NodeResource res = new NodeResource(plannedNode);
                                            if (planResource(leaseUid, res.toByteArray())) {
                                                nodeResources.put(leaseUid, nodeResource = res);
                                                continue;
                                            }
                                            if (plannedNode.future.cancel(true)) {
                                                LOGGER.log(Level.INFO, "Cancelled provisioning of {0}",
                                                        plannedNode.displayName);
                                                continue;
                                            }
                                            String excessLeaseId = UUID.randomUUID().toString();
                                            if (registerRequest(getUid(), excessLeaseId) && planResource(
                                                    excessLeaseId, res.toByteArray())) {
                                                nodeResources.put(excessLeaseId, res);
                                                continue;
                                            }
                                            LOGGER.log(Level.WARNING,
                                                    "Probable leak of resources from cloud {0}. "
                                                            + "Provisioned {1} but could not record provisioning",
                                                    new Object[]{cloud.getDisplayName(), plannedNode.displayName});
                                        }
                                    } else if (!updateState(leaseUid, REQUESTED, PLANNED) && status.equals(lastStatus)) {
                                        updateState(leaseUid, REQUESTED, DECOMMISSIONED);
                                    }
                                    break;
                                case PLANNED:
                                    if (nodeResource == null) {
                                        // invalid state
                                        updateState(leaseUid, PLANNED, DECOMMISSIONED);
                                    } else if (nodeResource.isProvisioned()) {
                                        Node node = nodeResource.getNode();
                                        if (node == null) {
                                            // failed to materialize
                                            updateState(leaseUid, PLANNED, DECOMMISSIONED);
                                        } else {
                                            // transition to next state
                                            updateStateAndResource(leaseUid, PLANNED, AVAILABLE, nodeResource.toByteArray());
                                        }
                                    }
                                    break;
                                case AVAILABLE:
                                    if (!registerLease(leaseUid, tenant) && status.equals(lastStatus)) {
                                        updateState(leaseUid, AVAILABLE, DECOMMISSIONING);
                                    }
                                    break;
                                case LEASED:
                                    if (tenant.equals(getTenant(leaseUid))) {
                                        // it's ours
                                        return newComputerLauncherFactory(leaseUid);
                                    }
                                    throw new ProvisioningException("Could not provision node");
                                case RETURNED:
                                    if (!reuseNodes || (!updateState(leaseUid, RETURNED, AVAILABLE) && status.equals(lastStatus))) {
                                        updateState(leaseUid, RETURNED, DECOMMISSIONING);
                                    }
                                    break;
                                case DECOMMISSIONING:
                                    Node node = nodeResource == null ? null : nodeResource.getNode();
                                    if (node instanceof AbstractCloudSlave) {
                                        final AbstractCloudSlave cloudSlave = (AbstractCloudSlave) node;
                                        try {
                                            cloudSlave.terminate();
                                            updateState(leaseUid, DECOMMISSIONING, DECOMMISSIONED);
                                        } catch (InterruptedException e) {
                                            LOGGER.log(Level.INFO, "Interrupted during decommissioning", e);
                                        } catch (IOException e) {
                                            LOGGER.log(Level.INFO, "IOException during decommissioning", e);
                                        }
                                    }
                                    break;
                                case DECOMMISSIONED:
                                    decommissionLease(leaseUid);
                                    nodeResources.remove(leaseUid);
                                    throw new ProvisioningException("Could not provision node");
                            }
                            lastStatus = status;
                            if (status.equals(getStatus(leaseUid))) {
                                try {
                                    Future<?> future;
                                    NodeProvisioner.PlannedNode plannedNode;
                                    if (nodeResource != null && (plannedNode = nodeResource.plannedNode) != null) {
                                        future = plannedNode.future;
                                    } else {
                                        future = SlaveLeaseListener.onChange(leaseUid);
                                    }
                                    future.get(10, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    // ignore
                                } catch (ExecutionException e) {
                                    // ignore
                                } catch (TimeoutException e) {
                                    // ignore
                                }
                            }
                        }
                    }
                }));
    }

    public void release(ComputerLauncherFactory allocatedSlave) {
        LeaseId leaseId = allocatedSlave.getLeaseId();
        if (leaseId instanceof DefaultLeaseId) {
            final String leaseUid = ((DefaultLeaseId) leaseId).getUuid();
            if (getUid().equals(getOwner(leaseUid))) {
                LeaseState status = getStatus(leaseUid);
                if (status == null || !LEASED.equals(status)) {
                    LOGGER.log(Level.INFO, "SharedCloud[{0}] could not record return of lease: {1}",
                            new Object[]{getUrl(), leaseUid});
                    return;
                }
                returnLease(leaseUid);
                Computer.threadPoolForRemoting.submit(new Runnable() {
                    public void run() {
                        LeaseState lastStatus = null;
                        while (true) {
                            LeaseState status = getStatus(leaseUid);
                            if (status == null) {
                                return;
                            }
                            NodeResource nodeResource = getNodeResource(leaseUid);
                            switch (status) {
                                case REQUESTED:
                                case PLANNED:
                                case AVAILABLE:
                                case LEASED:
                                    // somebody else is managing its state
                                    return;
                                case RETURNED:
                                    if (!reuseNodes || (!updateState(leaseUid, RETURNED, AVAILABLE) && status.equals(lastStatus))) {
                                        updateState(leaseUid, RETURNED, DECOMMISSIONING);
                                    }
                                    break;
                                case DECOMMISSIONING:
                                    Node node = nodeResource.getNode();
                                    if (node instanceof AbstractCloudSlave) {
                                        final AbstractCloudSlave cloudSlave = (AbstractCloudSlave) node;
                                        try {
                                            cloudSlave.terminate();
                                            updateState(leaseUid, DECOMMISSIONING, DECOMMISSIONED);
                                        } catch (InterruptedException e) {
                                            LOGGER.log(Level.INFO, "Interrupted during decommissioning", e);
                                        } catch (IOException e) {
                                            LOGGER.log(Level.INFO, "IOException during decommissioning", e);
                                        }
                                    }
                                    break;
                                case DECOMMISSIONED:
                                    decommissionLease(leaseUid);
                                    nodeResources.remove(leaseUid);
                                    break;
                            }
                            lastStatus = status;
                            if (status.equals(getStatus(leaseUid))) {
                                try {
                                    Future<?> future;
                                    NodeProvisioner.PlannedNode plannedNode;
                                    if (nodeResource != null && (plannedNode = nodeResource.plannedNode) != null) {
                                        future = plannedNode.future;
                                    } else {
                                        future = SlaveLeaseListener.onChange(leaseUid);
                                    }
                                    future.get(10, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    // ignore
                                } catch (ExecutionException e) {
                                    // ignore
                                } catch (TimeoutException e) {
                                    // ignore
                                }
                            }
                        }
                    }
                });
            } else {
                LOGGER.log(Level.INFO, "SharedCloud[{0}] returned an unknown lease: {1}",
                        new Object[]{getUrl(), leaseUid});
            }
        } else {
            LOGGER.log(Level.INFO, "SharedCloud[{0}] returned an unknown lease: {1}", new Object[]{getUrl(), leaseId});
        }
    }

    public boolean isProvisioned(LeaseId id) {
        if (id instanceof DefaultLeaseId) {
            Set<String> leases = getLeases(getUid());
            return leases != null && leases.contains(((DefaultLeaseId) id).getUuid());
        }
        return false;
    }

    public Map<LeaseId, Node> getLeaseIds() {
        Set<String> leases = getLeases(getUid());
        Map<LeaseId, Node> result = new LinkedHashMap<LeaseId, Node>(leases.size());
        for (String leaseId: leases) {
            result.put(new DefaultLeaseId(leaseId), getNode(leaseId));
        }
        return result;
    }

    private void requireDisabled() throws ServletException {
        if (!disabled) {
            throw new ServletException("Must be off-line");
        }
    }

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.SharedCloud_SlaveResource_DisplayName();
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            // TODO how to check for create permission?
            return new SharedCloud(parent, name);
        }

        public List<NodePropertyDescriptor> getNodePropertyDescriptors() {
            return Collections.emptyList();
        }

        public List<SharedCloudPropertyDescriptor> getSlavePropertyDescriptors() {
            return SharedCloudPropertyDescriptor.all();
        }

        public List<Descriptor<Cloud>> getCloudDescriptors() {
            return MetaNectar.allWithoutMetaNectarExtensions(Cloud.class);
        }

        public List<Descriptor<ComputerLauncher>> getComputerLauncherDescriptors() {
            DescriptorExtensionList<ComputerLauncher, Descriptor<ComputerLauncher>> unfiltered =
                    Hudson.getInstance().getDescriptorList(ComputerLauncher.class);
            return Lists.newArrayList(Iterables.filter(unfiltered,
                    SharedSlaveComputerLauncherPredicate.getInstance()));
        }

    }

    public static class PropertyList extends DescribableList<SharedCloudProperty<?>, SharedCloudPropertyDescriptor> {
        private PropertyList(SharedCloud owner) {
            super(owner);
        }

        public PropertyList() {// needed for XStream deserialization
        }

        public SharedCloud getOwner() {
            return (SharedCloud) owner;
        }

        @Override
        protected void onModified() throws IOException {
            if (owner instanceof SharedCloud) {
                for (SharedCloudProperty p : this) {
                    p.setOwner(getOwner());
                }
            }
        }
    }

    public static class CloudList extends DescribableList<Cloud, Descriptor<Cloud>> {
        public CloudList(SharedCloud h) {
            super(h);
        }

        public CloudList() {// needed for XStream deserialization
        }

        public Cloud getByName(String name) {
            for (Cloud c : this) {
                if (c.name.equals(name)) {
                    return c;
                }
            }
            return null;
        }

        @Override
        protected void onModified() throws IOException {
            super.onModified();
        }
    }

    @Extension
    public static class PeriodicTidyUp extends PeriodicWork {

        private final Map<String, LeaseState> previousStatuses = new HashMap<String, LeaseState>();

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(10);
        }

        @Override
        protected void doRun() throws Exception {
            Set<String> liveLeaseIds = new HashSet<String>();
            for (SharedCloud cloud : Hudson.getInstance().getAllItems(SharedCloud.class)) {
                for (LeaseRecord leaseRecord : getLeaseRecords(cloud.getUid())) {
                    liveLeaseIds.add(leaseRecord.getLeaseId());
                    LeaseState prevStatus = previousStatuses.get(leaseRecord.getLeaseId());
                    if (!leaseRecord.getStatus().equals(prevStatus)) {
                        previousStatuses.put(leaseRecord.getLeaseId(), leaseRecord.getStatus());
                        // progressing, can leave it alone
                        continue;
                    }
                    NodeResource nodeResource = cloud.getNodeResource(leaseRecord.getLeaseId());
                    switch (leaseRecord.getStatus()) {
                        case REQUESTED:
                            if (nodeResource == null && leaseRecord.getStatus().equals(prevStatus)) {
                                LOGGER.log(Level.INFO, "SharedCloud[{0}] : lease {1}. Progressing from {2} -> {3}",
                                        new Object[]{
                                                cloud.getUrl(),
                                                leaseRecord.getLeaseId(), leaseRecord.getStatus(), DECOMMISSIONED
                                        });
                                updateState(leaseRecord.getLeaseId(), REQUESTED, DECOMMISSIONED);
                            } else if (!updateState(leaseRecord.getLeaseId(), REQUESTED, PLANNED) && leaseRecord.getStatus().equals(prevStatus)) {
                                LOGGER.log(Level.INFO, "SharedCloud[{0}] : lease {1}. Progressing from {2} -> {3}",
                                        new Object[]{
                                                cloud.getUrl(),
                                                leaseRecord.getLeaseId(), leaseRecord.getStatus(), DECOMMISSIONED
                                        });
                                updateState(leaseRecord.getLeaseId(), REQUESTED, DECOMMISSIONED);
                            }
                            break;
                        case PLANNED:
                            if (nodeResource == null) {
                                // invalid state
                                updateState(leaseRecord.getLeaseId(), PLANNED, DECOMMISSIONED);
                            } else if (nodeResource.isProvisioned()) {
                                Node node = nodeResource.getNode();
                                if (node == null) {
                                    // failed to materialize
                                    LOGGER.log(Level.INFO, "SharedCloud[{0}] : lease {1}. Progressing from {2} -> {3}",
                                            new Object[]{
                                                    cloud.getUrl(),
                                                    leaseRecord.getLeaseId(), leaseRecord.getStatus(), DECOMMISSIONED
                                            });
                                    updateState(leaseRecord.getLeaseId(), PLANNED, DECOMMISSIONED);
                                } else {
                                    // transition to next state
                                    LOGGER.log(Level.INFO, "SharedCloud[{0}] : lease {1}. Progressing from {2} -> {3}",
                                            new Object[]{
                                                    cloud.getUrl(), leaseRecord.getLeaseId(),
                                                    leaseRecord.getStatus(), AVAILABLE
                                            });
                                    updateStateAndResource(leaseRecord.getLeaseId(), PLANNED, AVAILABLE, nodeResource.toByteArray());
                                }
                            }
                            break;
                        case AVAILABLE:
                        case RETURNED:
                            LOGGER.log(Level.INFO, "SharedCloud[{0}] : lease {1}. Progressing from {2} -> {3}",
                                    new Object[]{
                                            cloud.getUrl(), leaseRecord.getLeaseId(),
                                            leaseRecord.getStatus(), DECOMMISSIONING
                                    });
                            updateState(leaseRecord.getLeaseId(), leaseRecord.getStatus(), DECOMMISSIONING);
                            break;
                        case LEASED:
                            final String tenant = leaseRecord.getTenantId();
                            if (tenant != null) {
                                ConnectedMaster master = ConnectedMaster.findByUid(tenant);
                                if (master != null) {
                                    // check if lease is still held
                                    Set<LeaseId> leases = master.getSlaveManifest().getSlaves();

                                    if (leases == null) {
                                        // don't know, so cannot free
                                        break;
                                    }
                                    if (leases.contains(new DefaultLeaseId(leaseRecord.getLeaseId()))) {
                                        // still in use, so cannot free
                                        break;
                                    } else {
                                        // no longer in use, free the slave
                                    }
                                } else {
                                    // master does not exist, free the slave
                                }
                            }
                            LOGGER.log(Level.INFO, "SharedCloud[{0}] : lease {1}. Progressing from {2} -> {3}",
                                    new Object[]{
                                            cloud.getUrl(), leaseRecord.getLeaseId(),
                                            leaseRecord.getStatus(), RETURNED
                                    });
                            returnLease(leaseRecord.getLeaseId());
                            break;
                        case DECOMMISSIONING:
                            LOGGER.log(Level.INFO, "SharedCloud[{0}] : lease {1}. Progressing from {2} -> {3}",
                                    new Object[]{
                                            cloud.getUrl(), leaseRecord.getLeaseId(),
                                            leaseRecord.getStatus(), DECOMMISSIONED
                                    });
                            Node node = nodeResource.getNode();
                            if (node instanceof AbstractCloudSlave) {
                                final AbstractCloudSlave cloudSlave = (AbstractCloudSlave) node;
                                try {
                                    cloudSlave.terminate();
                                    updateState(leaseRecord.getLeaseId(), DECOMMISSIONING, DECOMMISSIONED);
                                } catch (InterruptedException e) {
                                    LOGGER.log(Level.INFO, "Interrupted during decommissioning", e);
                                } catch (IOException e) {
                                    LOGGER.log(Level.INFO, "IOException during decommissioning", e);
                                }
                            }
                            break;
                        case DECOMMISSIONED:
                            LOGGER.log(Level.INFO, "SharedCloud[{0}] : lease {1}. Decommissioning",
                                    new Object[]{cloud.getUrl(), leaseRecord.getLeaseId()});
                            decommissionLease(leaseRecord.getLeaseId());
                            cloud.nodeResources.remove(leaseRecord.getLeaseId());
                            break;
                    }
                }
            }
            previousStatuses.keySet().retainAll(liveLeaseIds);
        }

    }

    @Extension
    public static class NodeIteratorImpl extends NodeIterator {
        private final Iterator<SharedCloud> clouds;
        private Iterator<Node> delegate;

        public NodeIteratorImpl() {
            this.delegate = Hudson.getInstance().getNodes().iterator();
            this.clouds = Hudson.getInstance().getAllItems(SharedCloud.class).iterator();
        }

        /**
         * {@inheritDoc}
         */
        public synchronized boolean hasNext() {
            if (delegate != null && delegate.hasNext()) {
                return true;
            }
            while (clouds.hasNext()) {
                SharedCloud cloud = clouds.next();
                delegate = new NodeResourceCollectionNodeIterator(cloud.nodeResources.values());
                if (delegate.hasNext()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        public synchronized Node next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return delegate.next();
        }

        /**
         * {@inheritDoc}
         */
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    @CLIResolver
    public static SharedCloud resolveForCLI(
            @Argument(required = true, metaVar = "NAME", usage = "Shared cloud name") String name)
            throws CmdLineException {
        SharedCloud sharedCloud = MetaNectar.getInstance().getItemByFullName(name, SharedCloud.class);
        if (sharedCloud == null) {
            throw new CmdLineException(null, "No such shared cloud exists: " + name);
        }
        return sharedCloud;
    }

    private static class NodeResourceCollectionNodeIterator implements Iterator<Node> {
        private final Iterator<NodeResource> delegate;
        private Node next = null;

        private NodeResourceCollectionNodeIterator(Collection<NodeResource> nodeResources) {
            this.delegate = nodeResources.iterator();
        }

        public boolean hasNext() {
            while (next == null && delegate.hasNext()) {
                NodeResource nodeResource = delegate.next();
                next = nodeResource.getNode();
            }
            return next != null;
        }

        public Node next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            try {
                return next;
            } finally {
                next = null;
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static class NodeResource implements Serializable {

        private static final long serialVersionUID = 1L;

        private volatile Node node;
        private volatile transient NodeProvisioner.PlannedNode plannedNode;

        /**
         * Called when object has been deserialized from a stream.
         *
         * @return {@code this}, or a replacement for {@code this}.
         * @throws java.io.ObjectStreamException if the object cannot be restored.
         * @see <a href="http://download.oracle.com/javase/1.3/docs/guide/serialization/spec/input.doc6.html">The
         * Java Object Serialization Specification</a>
         */
        @SuppressWarnings("unused")
        private Object readResolve() throws ObjectStreamException {
            getNode();
            return this;
        }

        /**
         * Called when object is to be serialized on a stream to allow the object to substitute a proxy for itself.
         *
         * @return {@code this}, or the proxy for {@code this}.
         * @throws java.io.ObjectStreamException if the object cannot be proxied.
         * @see <a href="http://download.oracle.com/javase/1.3/docs/guide/serialization/spec/output.doc5.html">The
         * Java Object Serialization Specification</a>
         */
        @SuppressWarnings("unused")
        private Object writeReplace() throws ObjectStreamException {
            getNode();
            return this;
        }

        public boolean isProvisioned() {
            NodeProvisioner.PlannedNode plannedNode1 = plannedNode;
            return plannedNode1 == null || plannedNode1.future.isDone();
        }

        public Node getNode() {
            NodeProvisioner.PlannedNode plannedNode = this.plannedNode;
            if (plannedNode != null) {
                // Idempotent
                if (!plannedNode.future.isDone()) {
                    return null;
                }
                try {
                    node = plannedNode.future.get();
                    this.plannedNode = null;
                } catch (InterruptedException e) {
                    // ignore
                } catch (ExecutionException e) {
                    // ignore
                }
            }
            return node;
        }

        public NodeResource(Node node) {
            this.node = node;
            this.plannedNode = null;
        }

        public NodeResource(NodeProvisioner.PlannedNode plannedNode) {
            this.plannedNode = plannedNode;
            this.node = null;
        }

        public byte[] toByteArray() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Items.XSTREAM.toXML(this, out);
            return out.toByteArray();
        }

        public static NodeResource fromByteArray(byte[] bytes) {
            return (NodeResource) Items.XSTREAM.fromXML(new ByteArrayInputStream(bytes));
        }
    }

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(SharedCloud.class, Messages._SharedCloud_PermissionsTitle());

    // TODO
    // CREATE and DELETE are disabled until Jenkins core is modified to support finer-grained permission checking
    // for creation and deletion of items
    /*
    public static final Permission CREATE = new Permission(PERMISSIONS,"Create", Messages._SharedCloud_Create_Permission(), Item.CREATE);

    public static final Permission DELETE = new Permission(PERMISSIONS,"Delete", Messages._SharedCloud_Delete_Permission(), Item.DELETE);
    */

    public static final Permission CONFIGURE = new Permission(PERMISSIONS,"Configure", Messages._SharedCloud_Configure_Permission(), Item.CONFIGURE);

}
