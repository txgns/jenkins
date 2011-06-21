package metanectar.model;

import com.cloudbees.commons.metanectar.provisioning.SlaveManager;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.*;
import hudson.remoting.Channel;
import hudson.util.DescribableList;
import hudson.util.StreamTaskListener;
import hudson.util.io.ReopenableFileOutputStream;
import metanectar.provisioning.IdentifierFinder;
import metanectar.provisioning.ScopedSlaveManager;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A master template.
 *
 * @author Paul Sandoz
 */
public abstract class MasterTemplate extends AbstractItem implements TopLevelItem {

    /**
     * The states of the master.
     */
    public static enum State {
        Created(Action.Delete),
        Copying(),
        CopyingError(Action.Delete),
        Copied(Action.Delete);

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
     * The location of the archive.
     */
    protected volatile String archive;

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

    public synchronized void setCopyingState() throws IOException {
        setState(State.Copying);

        save();
    }

    public synchronized void setCopyingErrorState(Throwable error) throws IOException {
        setState(State.Copying);
        this.error = error;

        save();
    }

    public synchronized void setCopiedState() throws IOException {
        setState(State.Copied);

        save();
    }

    private void setState(State state) {
        this.state = state;
        this.error = null;
        this.timeStamp = new Date().getTime();
    }


    //

    private static final Logger LOGGER = Logger.getLogger(MasterTemplate.class.getName());
}