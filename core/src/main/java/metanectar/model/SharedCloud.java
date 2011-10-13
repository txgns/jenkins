package metanectar.model;

import antlr.ANTLRException;
import com.cloudbees.commons.futures.CompletedFuture;
import com.cloudbees.commons.metanectar.provisioning.ComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.DefaultLeaseId;
import com.cloudbees.commons.metanectar.provisioning.FutureComputerLauncherFactory;
import com.cloudbees.commons.metanectar.provisioning.LeaseId;
import com.cloudbees.commons.metanectar.provisioning.ProvisioningException;
import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import com.cloudbees.commons.metanectar.provisioning.SlaveManifest;
import com.cloudbees.commons.nectar.nodeiterator.NodeIterator;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.sun.org.apache.regexp.internal.RE;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Util;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.model.*;
import hudson.remoting.Channel;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.NodeProvisioner;
import hudson.util.DescribableList;
import hudson.util.TimeUnit2;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Represents a shared cloud
 *
 * @author Stephen Connolly
 */
public class SharedCloud extends AbstractItem implements TopLevelItem, SlaveManager {
    private static final Logger LOGGER = Logger.getLogger(SharedCloud.class.getName());
    // property state

    protected volatile DescribableList<SharedCloudProperty<?>, SharedCloudPropertyDescriptor> properties =
            new PropertyList(this);

    @GuardedBy("this")
    private Map<LeaseId, Node> leaseIds = new LinkedHashMap<LeaseId, Node>();

    private final Object nodeLock = new Object();

    @GuardedBy("nodeLock")
    private List<Node> provisionedNodes = new ArrayList<Node>();

    @GuardedBy("nodeLock")
    private List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<NodeProvisioner.PlannedNode>();

    @GuardedBy("nodeLock")
    private List<ReturnedNode> returnedNodes = new ArrayList<ReturnedNode>();

    private boolean reuseNodes;

    private int reuseTimeout;

    private Cloud cloud;

    private transient Runnable periodicWork;

    protected SharedCloud(ItemGroup parent, String name) {
        super(parent, name);
    }

    private Object readResolve() {
        synchronized (this) {
            if (leaseIds == null) {
                leaseIds = new LinkedHashMap<LeaseId, Node>();
            }
            if (provisionedNodes == null) {
                provisionedNodes = new ArrayList<Node>();
            }
            if (plannedNodes == null) {
                plannedNodes = new ArrayList<NodeProvisioner.PlannedNode>();
            }
            if (returnedNodes == null) {
                returnedNodes = new ArrayList<ReturnedNode>();
            }
        }

        if (!reuseNodes) {
            reuseTimeout = 1;
        }
        return this;
    }

    public boolean isBuilding() {
        synchronized (this) {
            return leaseIds != null && !leaseIds.isEmpty();
        }
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

    protected Runnable getPeriodicWork() {
        synchronized (nodeLock) {
            if (periodicWork == null) {
                periodicWork = new PeriodicWorkRunnable();
            }
            return periodicWork;
        }
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
                int instanceCount;
                synchronized (this) {
                    instanceCount = leaseIds == null ? 0 : leaseIds.size();
                }
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

    public synchronized void doConfigSubmit(StaplerRequest req,
                                            StaplerResponse rsp)
            throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

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

        final ArrayList<Node> nodes = new ArrayList<Node>();
        synchronized (this) {
            if (leaseIds == null) {
                leaseIds = new LinkedHashMap<LeaseId, Node>();
            } else {
                nodes.addAll(leaseIds.values());
                leaseIds.clear();
            }
            try {
                save();
            } catch (IOException e) {
                LOGGER.log(Level.INFO, "SharedCloud[{0}] could not persist", getUrl());
            }
        }
        synchronized (nodeLock) {
            for (Node node : nodes) {
                returnedNodes.add(new ReturnedNode(node, true));
            }
        }
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
    }

    public ComputerLauncherFactory newComputerLauncherFactory(LeaseId leaseId) {
        synchronized (this) {
            Node node = leaseIds.get(leaseId);
            if (node instanceof Slave) {
                final Slave slave = (Slave) node;
                return new SharedNodeComputerLauncherFactory(leaseId, node.getNodeName(), node.getNodeDescription(),
                        node.getNumExecutors(), node.getLabelString(), slave.getRemoteFS(), node.getMode(),
                        new SharedSlaveRetentionStrategy(), node.getNodeProperties().toList(),
                        slave.getLauncher());
            } else {
                return null;
            }
        }
    }

    public boolean canProvision(String labelExpression) {
        final Label label;
        try {
            label = labelExpression == null ? null : Label.parseExpression(labelExpression);
        } catch (ANTLRException e) {
            // if we don't understand the label expression we cannot provision it (might be a new syntax... our
            // problem)
            return false;
        }

        if (reuseNodes) {
            synchronized (nodeLock) {
                for (final ReturnedNode returnedNode : returnedNodes) {
                    Node node = returnedNode.getNode();
                    if (label == null
                            ? !Node.Mode.EXCLUSIVE.equals(node.getMode())
                            : label.matches(node.getAssignedLabels())) {
                        return true;
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

    public FutureComputerLauncherFactory provision(String labelExpression, TaskListener listener, int numOfExecutors)
            throws ProvisioningException {
        final Label label;
        try {
            label = labelExpression == null ? null : Label.parseExpression(labelExpression);
        } catch (ANTLRException e) {
            throw new ProvisioningException(e.getMessage(), e);
        }
        LOGGER.log(Level.INFO, "", new Object[]{});
        return new FutureComputerLauncherFactory(getName(), numOfExecutors,
                Computer.threadPoolForRemoting.submit(new Callable<ComputerLauncherFactory>() {
                    public ComputerLauncherFactory call() throws Exception {
                        int excessWorkload = 1; // TODO allow larger workloads

                        // The collection of nodes that are provisioning
                        Collection<NodeProvisioner.PlannedNode> provisioning =
                                new ArrayList<NodeProvisioner.PlannedNode>();

                        if (reuseNodes) {
                            LOGGER.info("Checking returned nodes for fast re-use...");
                            synchronized (nodeLock) {
                                for (Iterator<ReturnedNode> itr = returnedNodes.iterator(); itr.hasNext(); ) {
                                    final ReturnedNode returnedNode = itr.next();
                                    // Ignore any existing non-reuseable nodes, this can occur if the configuration
                                    // changes from non-reusable to reusable
                                    if (!returnedNode.isReuseable())
                                        continue;

                                    Node node = returnedNode.getNode();
                                    if (label == null
                                            ? !Node.Mode.EXCLUSIVE.equals(node.getMode())
                                            : label.matches(node.getAssignedLabels())) {
                                        LOGGER.log(Level.INFO, "SharedCloud[{0}] Quick reuse of node {1}",
                                                new Object[]{getUrl(), node.getNodeName()});
                                        provisioning.add(
                                                new NodeProvisioner.PlannedNode(
                                                        node.getDisplayName(),
                                                        new CompletedFuture<Node>(node), node.getNumExecutors()));
                                        itr.remove();
                                        excessWorkload -= node.getNumExecutors();

                                        // Avoiding removing more if the workload requirement has been reached
                                        if (excessWorkload <= 0)
                                            break;
                                    }
                                }
                            }

                            LOGGER.log(Level.INFO,
                                    "Checked returned nodes for fast re-use... {0} nodes being reused, "
                                            + "{1} will be provisioned",
                                    new Object[]{provisioning.size(), Math.max(0, excessWorkload)});
                        }


                        // If there is more workload then provision from the cloud
                        if (excessWorkload > 0) {
                            provisioning.addAll(cloud.provision(label, excessWorkload));
                        }

                        int plannedExecutorCount = 0;
                        for (NodeProvisioner.PlannedNode n : provisioning) {
                            plannedExecutorCount += n.numExecutors;
                        }
                        LOGGER.log(Level.INFO,
                                "SharedCloud[{0}] Asked for {1} executors, got a plan for {2} nodes with a total of "
                                        + "{3} executors.",
                                new Object[]{getUrl(), excessWorkload, provisioning.size(), plannedExecutorCount});


                        // Wait for all nodes to provision and transition from the provisioning to the
                        // provisioned list if successful
                        // The cloud.provision method may return more than one node so we need to handle all of them
                        // even if we only use one
                        LinkedList<Node> provisioned = new LinkedList<Node>();
                        while (!provisioning.isEmpty()) {
                            for (Iterator<NodeProvisioner.PlannedNode> itr = provisioning.iterator(); itr.hasNext(); ) {
                                NodeProvisioner.PlannedNode n = itr.next();

                                LOGGER.log(Level.INFO,
                                        "SharedCloud[{0}] waiting for provisioning of {2} executors on {1}",
                                        new Object[]{getUrl(), n.displayName, excessWorkload});
                                try {
                                    provisioned.add(n.future.get((excessWorkload >= 0) ? excessWorkload : 0, TimeUnit.SECONDS));
                                } catch (TimeoutException e) {
                                    LogRecord r = new LogRecord(Level.FINE,
                                            "SharedCloud[{0}] timed out while waiting for provisioning of {2} "
                                                    + "executors on {1}");
                                    r.setThrown(e);
                                    r.setParameters(new Object[]{getUrl(), n.displayName, excessWorkload});
                                    LOGGER.log(r);
                                    // TODO require a global timeout here
                                } catch (Exception e) {
                                    // ExecutionException, CancellationException or InterruptedException
                                    LogRecord r = new LogRecord(Level.FINE,
                                            "SharedCloud[{0}] failed to provision {2} executors on {1}");
                                    r.setThrown(e);
                                    r.setParameters(new Object[]{getUrl(), n.displayName, excessWorkload});
                                    LOGGER.log(r);
                                }

                                if (n.future.isDone()) {
                                    itr.remove();
                                }
                            }
                        }

                        // Process provisioned nodes, pop the first one off and use that, all others will
                        // be added to the returnedNodes list or the provisionedNodes
                        if (!provisioned.isEmpty()) {
                            Node provisionedNode = provisioned.pop();
                            for (Node n : provisioned) {
                                synchronized (nodeLock) {
                                    returnedNodes.add(new ReturnedNode(n, !reuseNodes));
                                }
                            }

                            synchronized (SharedCloud.this) {
                                try {
                                    LeaseId leaseId = new DefaultLeaseId(UUID.randomUUID().toString());
                                    leaseIds.put(leaseId, provisionedNode);
                                    LOGGER.log(Level.INFO, "SharedCloud[{0}] lent out {2} on lease {1}",
                                            new Object[]{getUrl(), leaseId, provisionedNode.getNodeName()});
                                    return newComputerLauncherFactory(leaseId);
                                } finally {
                                    try {
                                        save();
                                    } catch (IOException e) {
                                        LOGGER.log(Level.INFO, "SharedCloud[{0}] could not persist", getUrl());
                                    }
                                }
                            }
                        } else {
                            throw new ProvisioningException("Could not provision node");
                        }
                    }
                }));
    }

    public void release(ComputerLauncherFactory allocatedSlave) {
        synchronized (this) {
            if (leaseIds.containsKey(allocatedSlave.getLeaseId())) {
                LOGGER.log(Level.INFO, "SharedCloud[{0}] returned from lease {1}", new Object[]{getUrl(), leaseIds});
                Node node = leaseIds.remove(allocatedSlave.getLeaseId());
                if (node != null) {
                    synchronized (nodeLock) {
                        returnedNodes.add(new ReturnedNode(node, !reuseNodes));
                    }
                }
                this.notifyAll();
                try {
                    save();
                } catch (IOException e) {
                    LOGGER.log(Level.INFO, "SharedCloud[{0}] could not persist", getUrl());
                }
            }
        }
    }

    public boolean isProvisioned(LeaseId id) {
        synchronized (this) {
            return leaseIds.containsKey(id);
        }
    }

    public Map<LeaseId, Node> getLeaseIds() {
        synchronized (this) {
            Map<LeaseId, Node> result = new LinkedHashMap<LeaseId, Node>(leaseIds);
            return result;
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
    public static class PeriodicWorkScheduler extends PeriodicWork {

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(10);
        }

        @Override
        protected void doRun() throws Exception {
            for (SharedCloud cloud : MetaNectar.getInstance().getAllItems(SharedCloud.class)) {
                Computer.threadPoolForRemoting.submit(cloud.getPeriodicWork());
            }
        }
    }

    public class PeriodicWorkRunnable implements Runnable {

        public void run() {
            synchronized (nodeLock) {
                for (Iterator<NodeProvisioner.PlannedNode> itr = plannedNodes.iterator(); itr.hasNext(); ) {
                    NodeProvisioner.PlannedNode f = itr.next();
                    if (f.future.isDone()) {
                        try {
                            provisionedNodes.add(f.future.get());
                            nodeLock.notifyAll();
                            LOGGER.info(f.displayName + " provisioning successfully completed. We have now "
                                    + provisionedNodes.size() + " computer(s)");
                        } catch (InterruptedException e) {
                            throw new AssertionError(e); // since we confirmed that the future is already done
                        } catch (ExecutionException e) {
                            LOGGER.log(Level.WARNING, "Provisioned slave " + f.displayName + " failed to launch",
                                    e.getCause());
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Provisioned slave " + f.displayName + " failed to launch", e);
                        }
                        itr.remove();
                    }
                }
                for (Iterator<ReturnedNode> itr = returnedNodes.iterator(); itr.hasNext(); ) {
                    final ReturnedNode returnedNode = itr.next();
                    if (!reuseNodes || returnedNode.getTimestamp() + TimeUnit2.MINUTES.toMillis(reuseTimeout) < System
                            .currentTimeMillis()) {
                        Node node = returnedNode.getNode();
                        LOGGER.info(node.getNodeName() + " is being deprovisioned.");
                        if (node instanceof AbstractCloudSlave) {
                            final AbstractCloudSlave cloudSlave = (AbstractCloudSlave) node;
                            Computer.threadPoolForRemoting.submit(new Runnable() {
                                public void run() {
                                    try {
                                        cloudSlave.terminate();
                                    } catch (InterruptedException e) {
                                        LOGGER.log(Level.INFO, "Interrupted during release", e);
                                    } catch (IOException e) {
                                        LOGGER.log(Level.INFO, "IOException during release", e);
                                    }
                                }
                            });
                        }
                        itr.remove();
                    }
                }
            }
            boolean allConnected = true;
            Set<LeaseId> inUse = new HashSet<LeaseId>();
            for (ConnectedMaster master : MetaNectar.getInstance().getAllItems(ConnectedMaster.class)) {
                if (master.isOnline()) {
                    Channel channel = master.getChannel();
                    SlaveManifest slaveManifest;
                    try {
                        slaveManifest = (SlaveManifest) channel.getRemoteProperty(SlaveManifest.class);
                    } catch (ClassCastException e) {
                        allConnected = false;
                        LogRecord lr = new LogRecord(Level.INFO, "Class cast exception trying to talk to master {0}");
                        lr.setThrown(e);
                        lr.setParameters(new Object[]{master});
                        LOGGER.log(lr);
                        continue;
                    } catch (Throwable e) {
                        allConnected = false;
                        LogRecord lr = new LogRecord(Level.WARNING, "Unexpected exception from master {0}");
                        lr.setThrown(e);
                        lr.setParameters(new Object[]{master});
                        LOGGER.log(lr);
                        continue;
                    }
                    if (slaveManifest != null) {
                        inUse.addAll(slaveManifest.getSlaves());
                    } else {
                        LOGGER.log(Level.FINE, "No slave manifest available from master {0}", master);
                        allConnected = false;
                    }
                } else {
                    LOGGER.log(Level.INFO, "No connection to master {0}", master);
                    allConnected = false;
                }
            }
            Set<LeaseId> lentOut;
            synchronized (this) {
                lentOut = new HashSet<LeaseId>(leaseIds.keySet());
            }
            Set<LeaseId> missing = new HashSet<LeaseId>(lentOut);
            missing.removeAll(inUse);
            if (!missing.isEmpty()) {
                if (allConnected) {
                    LOGGER.log(Level.WARNING, "Some slave leases are missing: {0}", missing);
                    // if all connected we have the definitive list, so any missing can be returned
                    for (LeaseId missingId : missing) {
                        Node removed;
                        synchronized (this) {
                            removed = leaseIds.remove(missingId);
                        }
                        if (removed != null) {
                            synchronized (nodeLock) {
                                returnedNodes.add(new ReturnedNode(removed, true));
                            }
                        }
                        LOGGER.log(Level.INFO, "Recovered missing slave lease: {0}", missing);
                    }
                } else {
                    LOGGER.log(Level.INFO, "Some slave leases may be missing: {0}", missing);
                }
            }
            Set<LeaseId> unknown = new HashSet<LeaseId>(inUse);
            missing.removeAll(lentOut);
            if (!missing.isEmpty()) {
                LOGGER.log(Level.WARNING, "Some slave leases are unknown: {0}", unknown);
            }
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
                List<Node> list;
                SharedCloud cloud = clouds.next();
                synchronized (cloud.nodeLock) {
                    list = new ArrayList<Node>(cloud.provisionedNodes);
                }
                delegate = list.iterator();
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

    /**
     * Keeps track of returned nodes before being requested for deprovisioning.
     */
    private static class ReturnedNode {
        @NonNull
        private final Node node;
        private final long timestamp;

        public ReturnedNode(@NonNull Node node) {
            this(node, false);
        }

        public ReturnedNode(@NonNull Node node, boolean immediate) {
            node.getClass();
            this.node = node;
            timestamp = immediate ? Long.MIN_VALUE : System.currentTimeMillis();
        }

        @NonNull
        public Node getNode() {
            return node;
        }

        public boolean isReuseable() {
            return timestamp != Long.MIN_VALUE;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ReturnedNode that = (ReturnedNode) o;

            if (!node.equals(that.node)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(SharedCloud.class, Messages._SharedCloud_PermissionsTitle());

    public static final Permission CREATE = new Permission(PERMISSIONS,"Create", Messages._SharedCloud_Create_Permission(), Item.CREATE);

    public static final Permission DELETE = new Permission(PERMISSIONS,"Delete", Messages._SharedCloud_Delete_Permission(), Item.DELETE);

    public static final Permission CONFIGURE = new Permission(PERMISSIONS,"Configure", Messages._SharedCloud_Configure_Permission(), Item.CONFIGURE);

}
