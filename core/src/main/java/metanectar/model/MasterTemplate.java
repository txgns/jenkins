package metanectar.model;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import hudson.AbortException;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Util;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.model.*;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.kohsuke.stapler.*;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.security.Permissions;
import java.util.*;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static hudson.Util.fixEmpty;
import static java.util.Arrays.asList;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * A master template.
 *
 * @author Paul Sandoz
 */
public class MasterTemplate extends AbstractItem implements RecoverableTopLevelItem {

    /**
     * The states of the master.
     */
    public static enum State {
        Created(Action.Delete),
        Configured(Action.CloneFromSource, Action.Delete),
        Cloning(),
        CloningError(Action.CloneFromSource, Action.Delete),
        Cloned(Action.CloneToNewMaster, Action.Delete);

        public ImmutableSet<Action> actions;

        State(Action... actions) {
            this.actions = new ImmutableSet.Builder<Action>().add(actions).build();
        }

        public boolean canDo(Action a) {
            return actions.contains(a);
        }
    }

    /**
     * Actions that can be performed on a master.
     */
    public static enum Action {
        CloneFromSource("clipboard.png", CONFIGURE, "Clone from source"),
        CloneToNewMaster("clipboard.png", CLONE_MASTER, "Clone to new master"),
        Delete("trash-computer.png", DELETE);

        public final String icon;

        public final String displayName;

        public final String href;

        public final Permission permission;

        Action(String icon, Permission permission) {
            this.icon = icon;
            this.displayName = name();
            this.href = name().toLowerCase();
            this.permission = permission;
        }

        Action(String icon, Permission permission, String displayName) {
            this.icon = icon;
            this.displayName = displayName;
            this.href = name().toLowerCase();
            this.permission = permission;
        }
    }

    /**
     * The state of the master.
     */
    private volatile State state = State.Created;

    /**
     * The time stamp when the state was modified.
     *
     * @see {@link java.util.Date#getTime()}.
     */
    protected volatile long timeStamp;

    /**
     * Error associated with a particular state.
     */
    protected transient volatile Throwable error;

    /**
     * The source to obtain a template from.
     */
    protected volatile MasterTemplateSource source;

    /**
     * The template path name.
     */
    protected volatile TemplateFile template;

    /**
     * The properties
     */
    protected volatile DescribableList<MasterTemplateProperty, MasterTemplatePropertyDescriptor> properties =
            new PropertyList(this);

    public MasterTemplate(ItemGroup parent, String name) {
        super(parent, name);
    }

    public String toString() {
        return Objects.toStringHelper(this).
                add("name", name).
                toString();
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name)
            throws IOException {
        super.onLoad(parent, name);
        init();
    }

    @Override
    public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        init();
    }

    private void init() {
        if (properties == null) {
            properties = new PropertyList(this);
        } else {
            properties.setOwner(this);
        }
    }

    //

    public State getState() {
        return state;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public Throwable getError() {
        return error;
    }

    public MasterTemplateSource getSource() {
        return source;
    }

    public TemplateFile getTemplate() {
        return template;
    }

    public String getStatePage() {
        return state.name().toLowerCase();
    }

    public boolean isCloned() {
        return state == State.Cloned;
    }

    public boolean isSourceConfigurable() {
        switch (state) {
            case Created:
            case Configured:
            case CloningError:
                return true;
            default:
                return false;
        }
    }

    /**
     * No nested jobs
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


    // Methods for modifying state

    public synchronized void setCloningState() throws IOException {
        setState(State.Cloning);

        save();
    }

    public synchronized void setConfiguredState(MasterTemplateSource source) throws IOException {
        setState(State.Configured);
        this.source = source;
        save();
    }

    public synchronized void setCloningErrorState(Throwable error) throws IOException {
        setState(State.CloningError);
        this.error = error;

        save();
    }

    public synchronized void setClonedState(TemplateFile template) throws IOException {
        setState(State.Cloned);
        this.template = template;

        save();
    }

    private void setState(State state) {
        this.state = state;
        this.error = null;
        this.timeStamp = new Date().getTime();
    }


    // Icons

    public String getIcon() {
        return "package.png";
    }

    public StatusIcon getIconColor() {
        return new StockStatusIcon(getIcon(), Messages._MasterTemplate_Master_Template());
    }


    // Actions

    public Set<Action> getActionSet() {
        return ImmutableSet.copyOf(Action.values());
    }

    public ImmutableSet<Action> getValidActionSet() {
        return getState().actions;
    }

    public boolean canDoAction(Action a) {
        return state.canDo(a);
    }

    private void preConditionAction(Action a) throws IllegalStateException {
        if (!canDoAction(a)) {
            throw new IllegalStateException(String.format("Action \"%s\" cannot be performed when in state \"\"", a.name(), getState().name()));
        }
    }

    @CLIMethod(name="master-template-clone-from-source")
    public Future<MasterTemplate> cloneFromSourceAction() throws IOException {
        checkPermission(CONFIGURE);
        preConditionAction(Action.CloneFromSource);

        return MetaNectar.getInstance().masterProvisioner.cloneTemplateFromSource(this);
    }

    @CLIMethod(name="master-template-clone")
    public MasterServer cloneToNewMasterAction(
            @Option(name="-l", usage="The fully qualified path to the location where the template will be cloned to a new master") String location,
            @Option(name="-n", usage="The name of the new master") String name) throws Exception {
        checkPermission(CLONE_MASTER);
        preConditionAction(Action.CloneToNewMaster);

        location = location.substring(1);

        final MetaNectar mn = MetaNectar.getInstance();
        final ItemGroup ig = location.isEmpty() ? mn : (ItemGroup)mn.getItemByFullName(location, Item.class);

        final MasterServer m = createNewMaster(ig, name);
        final File snapshot = template.copyToSnapshot();
        m.setSnapshot(snapshot);

        return m;
    }

    private MasterServer createNewMaster(ItemGroup ig, String name) throws Exception {
        if (ig instanceof MetaNectar) {
            return ((MetaNectar)ig).createProject(MasterServer.class, name);
        } else {
            // TODO work around the restriction there is no common interface to create a child item of a container
            // For Folders this relies on the following method being present
            // public <T extends TopLevelItem> T createProject(Class<T> type, String name) throws IOException {

            Method createProject = ig.getClass().getMethod("createProject", Class.class, String.class);

            return (MasterServer)createProject.invoke(ig, MasterServer.class, name);
        }
    }

    // Recover

    public Future<MasterTemplate> initiateRecovery() throws Exception {
        final State unstableState = state;
        final Future<MasterTemplate> f = _initiateRecovery();
        if (f != null) {
            final String message = String.format("Initiating recovery of master template %s from state \"%s\"", this.getName(), unstableState);
            LOGGER.info(message);
            return f;
        } else {
            return Futures.immediateFuture(null);
        }
    }

    private Future<MasterTemplate> _initiateRecovery() throws Exception {
        switch (state) {
            case Cloning:
                return MetaNectar.getInstance().masterProvisioner.cloneTemplateFromSource(this);
            default:
                return null;
        }
    }

    // UI actions

    public HttpResponse doCloneFromSourceAction() throws Exception {
        return new DoActionLambda() {
            public void f() throws Exception {
                cloneFromSourceAction();
            }
        }.doAction();
    }

    public HttpResponse doCloneToNewMasterAction(StaplerRequest req, StaplerResponse rsp) throws Exception {
        requirePOST();

        JSONObject json = req.getSubmittedForm();

        String location = json.getString("location");

        String name = json.getString("masterName");

        MasterServer m = cloneToNewMasterAction(location, name);

        // Redirect to master configure page
        return HttpResponses.redirectViaContextPath(m.getUrl() + "configure");
    }

    private abstract class DoActionLambda {
        abstract void f() throws Exception;

        HttpResponse doAction() throws Exception {
            requirePOST();

            f();

            return HttpResponses.redirectToDot();
        }
    }

    @Override
    @CLIMethod(name="master-template-delete")
    public synchronized void delete() throws IOException, InterruptedException {
        checkPermission(DELETE);

        if (!canDoAction(Action.Delete)) {
            throw new AbortException(String.format("Action \"%s\" cannot be performed when in state \"\"", Action.Delete.name(), getState().name()));
        }

        if (state == State.Cloned) {
            template.getFile().delete();
        }

        super.delete();
    }


    // Properties

    public DescribableList<MasterTemplateProperty,MasterTemplatePropertyDescriptor> getProperties() {
        return properties;
    }

    public List<hudson.model.Action> getPropertyActions() {
        ArrayList<hudson.model.Action> result = new ArrayList<hudson.model.Action>();
        for (MasterTemplateProperty prop: properties) {
            result.addAll(prop.getMasterTemplateActions(this));
        }
        return result;
    }

    @Override
    public List<hudson.model.Action> getActions() {
        List<hudson.model.Action> result = new ArrayList<hudson.model.Action>(super.getActions());
        result.addAll(getPropertyActions());
        return Collections.unmodifiableList(result);
    }

    @Override
    public void addAction(hudson.model.Action a) {
        if(a==null) throw new IllegalArgumentException();
        super.getActions().add(a);
    }

    // Configuration

    public synchronized void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws Exception {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");
        try {
            JSONObject json = req.getSubmittedForm();

            if (isSourceConfigurable()) {
                setConfiguredState(req.bindJSON(MasterTemplateSource.class, json.getJSONObject("source")));
            }

            properties.rebuild(req,json.optJSONObject("properties"),MasterTemplateProperty.all());

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

        renameTo(newName);
        // send to the new job page
        // note we can't use getUrl() because that would pick up old name in the
        // Ancestor.getUrl()
        rsp.sendRedirect2(req.getContextPath() + '/' + getParent().getUrl()
                + getShortUrl());
    }

    //
    public static class PropertyList extends
            DescribableList<MasterTemplateProperty,MasterTemplatePropertyDescriptor> {
        private PropertyList(MasterTemplate owner) {
            super(owner);
        }

        public PropertyList() {// needed for XStream deserialization
        }

        public MasterTemplate getOwner() {
            return (MasterTemplate) owner;
        }

        @Override
        protected void onModified() throws IOException {
            if (owner instanceof MasterTemplate) {
                for (MasterTemplateProperty p : this) {
                    p.setOwner(getOwner());
                }
            }
        }
    }

    public String getLocation() {
        MetaNectar mn = MetaNectar.getInstance();
        if (mn.hasPermission(Item.READ)) {
            return "/" + mn.getFullName();
        }

        List<Item> items = MetaNectar.getInstance().getAllItems(Item.class);
        if (items.size() > 0) {
            return "/" + items.get(0).getFullName();
        } else {
            return "";
        }
    }

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return "Master Template";
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            // TODO how to check for create permission?
            return new MasterTemplate(parent, name);
        }

        public DescriptorExtensionList<MasterTemplateSource, Descriptor<MasterTemplateSource>> getMasterTemplateSourceDescriptors() {
            return MasterTemplateSource.all();
        }

        public FormValidation doCheckMasterName(@QueryParameter String location, @QueryParameter String masterName) {
            MetaNectar mn = MetaNectar.getInstance();

            if(Util.fixEmpty(masterName)==null)
                return FormValidation.ok();

            location = location.substring(1);

            if (location.isEmpty()) {
                return mn.doCheckJobName(masterName);
            } else {
                Item i = mn.getItemByFullName(location, Item.class);

                i.checkPermission(CREATE);

                ItemGroup ig = (ItemGroup)i;

                try {
                    checkJobName(ig, masterName);
                    return FormValidation.ok();
                } catch (Failure e) {
                    return FormValidation.error(e.getMessage());
                }
            }
        }

        private String checkJobName(ItemGroup ig, String name) throws Failure {
            MetaNectar.checkGoodName(name);
            name = name.trim();
            if(ig.getItem(name)!=null)
                throw new Failure(hudson.model.Messages.Hudson_JobAlreadyExists(name));
            // looks good
            return name;
        }

        public ListBoxModel doFillLocationItems() {
            ListBoxModel m = new ListBoxModel();

            List<ItemGroup> items = Lists.newArrayList();

            MetaNectar mn = MetaNectar.getInstance();
            if (mn.hasPermission(MasterServer.CREATE)) {
                items.add(mn);
            }

            for (Item i : MetaNectar.getInstance().getAllItems(Item.class)) {
                if (i instanceof ItemGroup && i.hasPermission(MasterServer.CREATE)) {
                    items.add((ItemGroup)i);
                }
            }

            for (ItemGroup ig : items) {
                m.add("/" + ig.getFullName());
            }

            return m;
        }

    }

    @CLIResolver
    public static MasterTemplate resolveForCLI(
            @Argument(required=true, metaVar="NAME", usage="Master template name") String name) throws CmdLineException {
        MasterTemplate template = MetaNectar.getInstance().getItemByFullName(name, MasterTemplate.class);
        if (template == null)
            throw new CmdLineException(null,"No such master template exists: " + name);
        return template;
    }

    public static final PermissionGroup PERMISSIONS = new PermissionGroup(MasterTemplate.class, Messages._MasterTemplate_PermissionsTitle());

    // TODO
    // CREATE and DELETE are disabled until Jenkins core is modified to support finer-grained permission checking
    // for creation and deletion of items
    /*
    public static final Permission CREATE = new Permission(PERMISSIONS,"Create", Messages._MasterTemplate_Create_Permission(), Item.CREATE);

    public static final Permission DELETE = new Permission(PERMISSIONS,"Delete", Messages._MasterTemplate_Delete_Permission(), Item.DELETE);
    */

    public static final Permission CONFIGURE = new Permission(PERMISSIONS,"Configure", Messages._MasterTemplate_Configure_Permission(), Item.CONFIGURE);

    public static final Permission CLONE_MASTER = new Permission(PERMISSIONS,"Clone", Messages._MasterTemplate_CloneMaster_Permission(), Hudson.ADMINISTER);


    private static final Logger LOGGER = Logger.getLogger(MasterTemplate.class.getName());
}