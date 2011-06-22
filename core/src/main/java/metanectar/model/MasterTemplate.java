package metanectar.model;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import hudson.AbortException;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.*;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * A master template.
 *
 * @author Paul Sandoz
 */
public class MasterTemplate extends AbstractItem implements TopLevelItem {

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
        CloneFromSource("clipboard.png", "Clone from source"),
        CloneToNewMaster("clipboard.png", "Clone to new master"),
        Delete("trash-computer.png");

        public final String icon;

        public final String displayName;

        public final String href;

        Action(String icon) {
            this.icon = icon;
            this.displayName = name();
            this.href = name().toLowerCase();
        }

        Action(String icon, String displayName) {
            this.icon = icon;
            this.displayName = displayName;
            this.href = name().toLowerCase();
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
    protected volatile String templatePath;

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

    public String getTemplatePath() {
        return templatePath;
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

    public synchronized void setCloningErrorState(Throwable error) throws IOException {
        setState(State.CloningError);
        this.error = error;

        save();
    }

    public synchronized void setClonedState(String templatePath) throws IOException {
        setState(State.Cloned);
        this.templatePath = templatePath;

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

    public synchronized void cloneFromSourceAction() throws IOException {
        preConditionAction(Action.CloneFromSource);

        MetaNectar.getInstance().masterProvisioner.cloneTemplateFromSource(this);
    }

    public synchronized void cloneToNewMasterAction() throws IOException {
        throw new UnsupportedOperationException("To be implemented");
    }

    // UI actions

    public HttpResponse doCloneFromSourceAction() throws Exception {
        return new DoActionLambda() {
            public void f() throws Exception {
                cloneFromSourceAction();
            }
        }.doAction();
    }

    public HttpResponse doCloneToNewMasterAction() throws Exception {
        return new DoActionLambda() {
            public void f() throws Exception {
                cloneToNewMasterAction();
            }
        }.doAction();
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
    public synchronized void delete() throws IOException, InterruptedException {
        if (!canDoAction(Action.Delete)) {
            throw new AbortException(String.format("Action \"%s\" cannot be performed when in state \"\"", Action.Delete.name(), getState().name()));
        }

        if (state == State.Cloned) {
            new File(templatePath).delete();
        }

        super.delete();
    }


    // Configuration

    public synchronized void doConfigSubmit(StaplerRequest req,
            StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        description = req.getParameter("description");
        try {
            JSONObject json = req.getSubmittedForm();

            if (isSourceConfigurable()) {
                state = State.Configured;
                source = req.bindJSON(MasterTemplateSource.class, json.getJSONObject("source"));
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

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {
        @Override
        public String getDisplayName() {
            return "Master Template";
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new MasterTemplate(parent, name);
        }

        public DescriptorExtensionList<MasterTemplateSource, Descriptor<MasterTemplateSource>> getMasterTemplateSourceDescriptors() {
            return MasterTemplateSource.all();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MasterTemplate.class.getName());
}