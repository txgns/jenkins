package metanectar.model;

import com.cloudbees.commons.metanectar.agent.AgentListener;
import com.cloudbees.commons.metanectar.agent.AgentStatusListener;
import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol.GracefulConnectionRefusalException;
import hudson.PluginManager;
import hudson.model.*;
import hudson.remoting.Channel;
import hudson.util.AdministrativeError;
import hudson.util.FormValidation;
import hudson.views.StatusColumn;
import metanectar.model.views.MasterServerColumn;
import metanectar.provisioning.MasterProvisioner;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jvnet.hudson.reactor.ReactorException;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.logging.Logger;

import static hudson.Util.fixEmpty;

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

    public static final String METANECTAR_ROOT_URL = System.getProperty("METANECTAR_ROOT_URL");

    public static final String GRANT_PROPERTY = "grant";

    private transient AgentListener nectarAgentListener;

    public transient final MasterProvisioner masterProvisioner = new MasterProvisioner();

    public static class AgentProtocolListener extends MetaNectarAgentProtocol.Listener {
        private final MetaNectar metaNectar;

        public AgentProtocolListener(MetaNectar metaNectar) {
            this.metaNectar = metaNectar;
        }

        public URL getEndpoint() throws IOException {
            return new URL(metaNectar.getRootUrl());
        }

        public void onConnectingTo(URL address, X509Certificate identity, String organization, Map<String, String> properties) throws GeneralSecurityException, IOException {
            final MasterServer server = metaNectar.getMasterByOrganization(organization);

            if (server == null) {
                throw new GeneralSecurityException("The master " + organization + " does not exist");
            }

            if (server.isTerminating()) {
                throw new GeneralSecurityException("The master " + organization + " is terminating");
            }

            if (server.isApproved()) {
                if (server.getIdentity().equals(identity.getPublicKey())) {
                    LOGGER.info("Master is identified and approved: " + organization + " " + address);
                } else {
                    throw new GracefulConnectionRefusalException("The master " + organization + " identity does not match");
                }
                return;
            }

            // Check if there is a grant for automatic registration
            if (properties.containsKey(GRANT_PROPERTY)) {
                final String receivedGrant = properties.get(GRANT_PROPERTY);

                if (server.getGrantId() != null) {
                    if (server.getGrantId().equals(receivedGrant)) {
                        server.setApprovedState((RSAPublicKey) identity.getPublicKey(), address);
                        LOGGER.info("Valid grant received. Master is identified and approved: " + organization + " " + address);
                        return;
                    } else {
                        // Grant is not the same that was issued
                        throw new GracefulConnectionRefusalException("Invalid grant for master " + organization);
                    }
                } else {
                    throw new IllegalStateException("The master " + organization + " has no grant");
                }
            }

            LOGGER.info("Master is not approved: "+ organization + " " + address);
            throw new GracefulConnectionRefusalException("This master is not approved by MetaNectar");
        }

        public void onConnectedTo(Channel channel, X509Certificate identity, String organization) throws IOException {
            final MasterServer server = metaNectar.getMasterByOrganization(organization);

            if (server != null) {
                server.setConnectedState(channel);
                return;
            }

            channel.close();
            throw new IOException("Unable to route the connection. No server found");
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
        super(root, context, pluginManager);

        configureNectarAgentListener(new AgentProtocolListener(this));
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
                null);

        try {
            nectarAgentListener = new AgentListener(asl, 0, Collections.singletonList(p));
            new Thread(nectarAgentListener, "MetaNectar agent listener port=" + nectarAgentListener.getPort()).start();
        } catch (BindException e) {
            new AdministrativeError(getClass().getName()+".tcpBind",
                    "Failed to listen to incoming agent connection",
                    "Failed to listen to incoming agent connection. <a href='configure'>Change the port number</a> to solve the problem.",e);
        }
    }

    private String rootUrl;

    @Override
    public String getRootUrl() {
        if (METANECTAR_ROOT_URL != null) {
            return METANECTAR_ROOT_URL;
        }

        if (rootUrl != null) {
            return rootUrl;
        }

        // TODO Hudson.getRootUrl() is returning null
        return super.getRootUrl();
    }

    public void setRootUrl(String rootUrl) {
        this.rootUrl = rootUrl;
    }

    public URL getRootUrlAsURL() {
        try {
            return new URL(getRootUrl());
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

    public MasterServer getMasterByIdentity(PublicKey identity) {
        for (MasterServer js : getItems(MasterServer.class))
            if (js.getIdentity().equals(identity))
                return js;
        return null;
    }

    public MasterServer getMasterByOrganization(String organization) {
        return (MasterServer)getItem(organization);
    }

    /**
     * Sets up the initial view state.
     */
    @Override
    protected View createInitialView() {
        try {
            MasterServerListView lv = new MasterServerListView("All");
            lv.setColumns(Arrays.asList(
                    new StatusColumn(),
                    new MasterServerColumn()));
            return lv;
        } catch (IOException e) {
            // view doesn't save itself unless it's connected to the parent, which we don't do in this method.
            // so this never happens
            throw new AssertionError(e);
        }
    }

    /**
     * Create a grant for a master to be provisioned automatically.
     */
    private String createGrantForMaster() {
        return UUID.randomUUID().toString();
    }

    public MasterServer createMasterServer(String organization) throws IOException {
        checkOrganizationName(organization);

        final MasterServer server = createProject(MasterServer.class, organization);

        server.setCreatedState(createGrantForMaster());
        return server;
    }

    private void checkOrganizationName(String name) {
        checkPermission(Item.CREATE);

        checkGoodName(name);
        name = name.trim();
        if (getItem(name) != null)
            throw new Failure("Organization " + name + "already exists");
    }

    public FormValidation doCheckOrganizationName(@QueryParameter String value) {
        // this method can be used to check if a file exists anywhere in the file system,
        // so it should be protected.
        checkPermission(Item.CREATE);

        if(fixEmpty(value)==null)
            return FormValidation.ok();

        try {
            checkOrganizationName(value);

            return FormValidation.ok();
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }
    }

    /**
     * Provision a new master and issue a grant for automatic approval.
     *
     */
    public MasterServer doProvisionMasterServer(String organization) throws IOException {
        final MasterServer server = createMasterServer(organization);

        provisionMaster(server);

        return server;
    }

    /**
     * Attach to a new master and issue a grant for automatic approval.
     *
     */
    public MasterServer doAttachMasterServer(String organization) throws IOException {
        final MasterServer server = createMasterServer(organization);

        return server;
    }

    /**
     * Provision a new master and issue a grant for automatic approval.
     *
     */
    public void provisionMaster(MasterServer server) throws IOException {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(GRANT_PROPERTY, server.getGrantId());
        masterProvisioner.provision(server, getRootUrlAsURL(), properties);
    }

    //

    public static MetaNectar getInstance() {
        return (MetaNectar)Hudson.getInstance();
    }
}