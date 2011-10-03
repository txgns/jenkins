package metanectar.model;

import com.cloudbees.commons.metanectar.agent.AgentListener;
import com.cloudbees.commons.metanectar.agent.AgentStatusListener;
import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol.GracefulConnectionRefusalException;
import com.cloudbees.commons.metanectar.utils.NotSecretXStream;
import com.google.common.base.Predicate;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.PluginManager;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.remoting.Channel;
import hudson.tasks.Mailer;
import hudson.util.AdministrativeError;
import hudson.util.AlternativeUiTextProvider;
import metanectar.Config;
import metanectar.ExtensionFilter;
import metanectar.MetaNectarExtensionPoint;
import metanectar.provisioning.*;
import metanectar.proxy.ReverseProxyProdder;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jvnet.hudson.reactor.ReactorException;

import javax.annotation.Nullable;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The root object of MetaNectar.
 *
 * Extends from {@link Hudson} to keep existing code working.
 *
 * @author Paul Sandoz
 * @author Kohsuke Kawaguchi
 */
public class MetaNectar extends Hudson {
    private static final Logger LOGGER = Logger.getLogger(MetaNectar.class.getName());

    private transient AgentListener nectarAgentListener;

    private transient Config config;

    public transient final MasterProvisioner masterProvisioner;

    public static class AgentProtocolListener extends MetaNectarAgentProtocol.Listener {
        private final MetaNectar metaNectar;

        public AgentProtocolListener(MetaNectar metaNectar) {
            this.metaNectar = metaNectar;
        }

        public URL getEndpoint() throws IOException {
            return new URL(metaNectar.getRootUrl());
        }

        public void onConnectingTo(URL address, X509Certificate identity, String name, Map<String, String> properties) throws GeneralSecurityException, IOException {
            final ConnectedMaster master = metaNectar.getConnectedMasterByName(name);

            if (master == null) {
                throw new IllegalStateException("The master " + name + " does not exist");
            }

            if (!master.isApprovable()) {
                throw new IllegalStateException("The master " + name + " is not in an approvable state: " + master.toString());
            }

            if (master.isApproved()) {
                if (master.getIdentityPublicKey().equals(identity.getPublicKey())) {
                    master.setReapprovedState();
                    LOGGER.info("Master is identified and approved: " + name + " " + address);
                    return;
                }

                throw new GeneralSecurityException("The master " + name + " identity does not match that which was previously approved");
            }

            if (properties.containsKey(MasterProvisioningService.PROPERTY_PROVISION_GRANT_ID)) {
                // Check if there is a grant for registration
                final String receivedGrant = properties.get(MasterProvisioningService.PROPERTY_PROVISION_GRANT_ID);

                if (master.getGrantId().equals(receivedGrant)) {
                    master.setApprovedState((RSAPublicKey) identity.getPublicKey(), address);
                    LOGGER.info("Valid grant received. Master is identified and approved: " + name + " " + address);
                    return;
                }

                GeneralSecurityException e = new GeneralSecurityException("Invalid grant for master " + name + ": received " + receivedGrant + " expected " + master.getGrantId());
                master.setApprovalErrorState(e);
                throw e;
            }

            LOGGER.info("Master is not approved: "+ name + " " + address);
            GracefulConnectionRefusalException e = new GracefulConnectionRefusalException("This master is not approved by MetaNectar");
            master.setApprovalErrorState(e);
            throw e;
        }

        public void onConnectedTo(Channel channel, X509Certificate identity, String name) throws IOException {
            final ConnectedMaster master = metaNectar.getConnectedMasterByName(name);

            if (master != null) {
                master.setConnectedState(channel);
                return;
            }

            channel.close();
            throw new IOException("Unable to route the connection. No master found");
        }

        @Override
        public void onRefusal(GracefulConnectionRefusalException e) throws Exception {
            throw e;
        }

        @Override
        public void onError(Exception e) throws Exception {
            throw e;
        }
    }

    public MetaNectar(File root, ServletContext context) throws IOException, InterruptedException, ReactorException {
        this(root, context, null);
    }

    public MetaNectar(File root, ServletContext context, PluginManager pluginManager) throws IOException, InterruptedException, ReactorException {
        this(root, context, pluginManager, Config.getInstance());
    }

    public MetaNectar(File root, ServletContext context, PluginManager pluginManager, Config config) throws IOException, InterruptedException, ReactorException {
        super(root, context, pluginManager);

        this.config = config;

        final URL u = config.getEndpoint();
        if (u != null) {
            Mailer.descriptor().setHudsonUrl(u.toExternalForm());
        }

        // TODO, the timeouts should be configurable
        this.masterProvisioner = new MasterProvisioner(this, TimeUnit.MINUTES.toMillis(5), TimeUnit.MINUTES.toMillis(5));

        configureNectarAgentListener(new AgentProtocolListener(this));


        if (!getConfig().isMasterProvisioning()) {
            // If master provisioning is disabled then remove the master provisioning node property if present
            MetaNectar.getInstance().getNodeProperties().removeAll(MasterProvisioningNodeProperty.class);
        }

        // Set up reverse proxy prodding if reload script is configured
        Config.ProxyProperties pp = getConfig().getBean(Config.ProxyProperties.class);
        if (pp.getReload() != null) {
            LOGGER.info("Configuring reverse proxy prodder");

            ReverseProxyProdder rpp = new ReverseProxyProdder(this, pp.getReload());
            MasterServerListener.all().add(0, rpp);

            // Prod on initialization
            rpp.prod();
        }

        // Initiate recovery for all recoverable items
        // TODO check initiate recovery implementations to see if they require that a node or master is connected to metanectar
        for (RecoverableTopLevelItem ri : getAllItems(RecoverableTopLevelItem.class)) {
            try {
                ri.initiateRecovery();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error initiating recovery for top-level item " + ri.getFullName(), e);
            }
        }
    }

    @Override
    public LabelAtom getSelfLabel() {
        return getLabelAtom("metamaster");
    }

    public void configureNectarAgentListener(MetaNectarAgentProtocol.Listener l) throws IOException {
        if (nectarAgentListener != null)
            nectarAgentListener.shutdown();

        AgentStatusListener asl = new AgentStatusListener.LoggerListener(LOGGER);

        InstanceIdentity id = InstanceIdentity.get();

        MetaNectarAgentProtocol.Inbound p = new MetaNectarAgentProtocol.Inbound(
                MetaNectarAgentProtocol.getInstanceIdentityCertificate(id, this),
                id.getPrivate(),
                "MetaNectar",
                Collections.<String, String>emptyMap(),
                l,
                Hudson.getInstance().getPluginManager().uberClassLoader);

        try {
            nectarAgentListener = new AgentListener(asl, 0, Collections.singletonList(p));
            new Thread(nectarAgentListener, "MetaNectar agent listener port=" + nectarAgentListener.getPort()).start();
        } catch (BindException e) {
            new AdministrativeError(getClass().getName()+".tcpBind",
                    "Failed to listen to incoming agent connection",
                    "Failed to listen to incoming agent connection. <a href='configure'>Change the port number</a> to solve the problem.",e);
        }
    }

    /**
     * @deprecated
     *      Use {@code Mailer.descriptor().setHudsonUrl(rootUrl)}
     */
    public void setRootUrl(String rootUrl) {
        Mailer.descriptor().setHudsonUrl(rootUrl);
    }

    public URL getMetaNectarPortUrl() {
        try {
            String rootUrl = getRootUrl();
            if (!rootUrl.endsWith("/"))
                rootUrl += "/";

            return new URL(rootUrl + MetaNectarPortRootAction.URL_NAME + "/");
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    public AgentListener getNectarAgentListener() {
        return nectarAgentListener;
    }

    @Override
    public void cleanUp() {
        super.cleanUp();

        if(nectarAgentListener != null)
            nectarAgentListener.shutdown();
    }

    @Override
    public String getDisplayName() {
        return Messages.MetaNectar_DisplayName();
    }

    public List<ConnectedMaster> getConnectedMasters() {
        // TODO make this more efficient by caching the masters and modifying when creating/deleting
        return getAllItems(ConnectedMaster.class);
    }

    public ConnectedMaster getConnectedMasterByIdentity(PublicKey identity) {
        for (ConnectedMaster js : getConnectedMasters())
            if (js.getIdentityPublicKey().equals(identity))
                return js;
        return null;
    }

    public ConnectedMaster getConnectedMasterByName(String idName) {
        for (ConnectedMaster ms : getConnectedMasters()) {
            if (ms.getIdName().equals(idName)) {
                return ms;
            }
        }

        return null;
    }

    public List<MasterServer> getManagedMasters() {
        // TODO make this more efficient by caching the masters and modifying when creating/deleting
        return getAllItems(MasterServer.class);
    }

    public MasterServer getManagedMasterByIdentity(PublicKey identity) {
        for (MasterServer js : getManagedMasters())
            if (js.getIdentityPublicKey().equals(identity))
                return js;
        return null;
    }

    public MasterServer getManagedMasterByName(String idName) {
        for (MasterServer ms : getManagedMasters()) {
            if (ms.getIdName().equals(idName)) {
                return ms;
            }
        }

        return null;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public Config getConfig() {
        return config;
    }


    // Master creation

    public MasterServer createManagedMaster(String name) throws IOException {
        checkItemName(name);

        return createProject(MasterServer.class, name);
    }

    public AttachedMaster createAttachedMaster(String name) throws IOException {
        checkItemName(name);

        return createProject(AttachedMaster.class, name);
    }

    // Template creation

    public MasterTemplate createMasterTemplate(String name) throws IOException {
        checkItemName(name);

        return createProject(MasterTemplate.class, name);
    }

    private void checkItemName(String name) {
        checkPermission(Item.CREATE);

        checkGoodName(name);
        name = name.trim();
        if (getItem(name) != null)
            throw new Failure("Item " + name + " already exists");
    }

    //

    /**
     * Obtain the list of descriptors for a describable class whose sub-classes are assignable
     * to {@link MetaNectarExtensionPoint}.
     *
     * @param describableClass the describable class
     * @param <T> the describable type
     * @param <D> the descriptor type
     * @return the sub-list of descriptors
     */
    public static <T extends Describable<T>, D extends Descriptor<T>> List<D> allWithMetaNectarExtensions(Class<T> describableClass) {
        return allWithPredicate(describableClass,
                new Predicate<Class<? extends T>>() {
                    public boolean apply(@Nullable Class<? extends T> input) {
                        return MetaNectarExtensionPoint.class.isAssignableFrom(input);
                    }
                });
    }

    /**
     * Obtain the list of descriptors for a describable class whose sub-classes are not assignable
     * to {@link MetaNectarExtensionPoint}.
     *
     * @param describableClass the describable class
     * @param <T> the describable type
     * @param <D> the descriptor type
     * @return the sub-list of descriptors
     */
    public static <T extends Describable<T>, D extends Descriptor<T>> List<D> allWithoutMetaNectarExtensions(Class<T> describableClass) {
        return allWithPredicate(describableClass,
                new Predicate<Class<? extends T>>() {
                    public boolean apply(@Nullable Class<? extends T> input) {
                        return !MetaNectarExtensionPoint.class.isAssignableFrom(input);
                    }
                });
    }

    private static <T extends Describable<T>, D extends Descriptor<T>> List<D> allWithPredicate(Class<T> describableClass, Predicate<Class<? extends T>> predicate) {
        final DescriptorExtensionList<T, D> unfiltered = MetaNectar.getInstance().getDescriptorList(describableClass);

        final List<D> filtered = new ArrayList<D>(unfiltered.size());

        for (D d : unfiltered) {
            if (predicate.apply(d.clazz)) {
                filtered.add(d);
            }
        }

        return filtered;
    }

    public static MetaNectar getInstance() {
        return (MetaNectar)Hudson.getInstance();
    }

    @Extension
    public static class PronounProvider extends AlternativeUiTextProvider {

        @Override
        public <T> String getText( Message<T> text, T context ) {
            if (context instanceof MasterServer) {
                if (AbstractItem.PRONOUN.equals( text )) {
                    return Messages.Master_Pronoun();
                }
            }
            return null;
        }
    }

    /**
     * Initialize the extension filters.
     * <p>
     * Potentially another plugin/component could explicitly add extensions within the PLUGINS_STARTED and
     * EXTENSIONS_AUGMENTED milestones, but since this functionality is deprecated it might be rare.
     * </p>
     */
    @Initializer(after = InitMilestone.PLUGINS_STARTED, before = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void initializeExtensionFilters(Hudson hudson) throws IOException {
        ExtensionFilter.defaultFilter(hudson);
    }

    static {
        // TODO sort out classloading so that we don't have to preload this class
        Logger.getLogger(NotSecretXStream.class.getName()).log(Level.INFO, "Loaded class {0}", NotSecretXStream.class);
    }
}