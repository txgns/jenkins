package metanectar.model;

import com.cloudbees.commons.metanectar.agent.AgentListener;
import com.cloudbees.commons.metanectar.agent.AgentStatusListener;
import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol.GracefulConnectionRefusalException;
import com.thoughtworks.xstream.core.util.Base64Encoder;
import hudson.PluginManager;
import hudson.Util;
import hudson.model.Failure;
import hudson.model.Hudson;
import hudson.model.View;
import hudson.remoting.Channel;
import hudson.util.AdministrativeError;
import hudson.util.IOException2;
import hudson.util.IOUtils;
import hudson.views.StatusColumn;
import metanectar.model.views.JenkinsServerColumn;
import metanectar.provisioning.MasterProvisioner;
import metanectar.provisioning.MasterProvisioningService;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jvnet.hudson.reactor.ReactorException;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.QueryParameter;
import org.omg.CORBA.PUBLIC_MEMBER;

import javax.servlet.ServletContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.*;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    private static final String METANECTAR_ROOT_URL = System.getProperty("METANECTAR_ROOT_URL");

    public static final String GRANT_PROPERTY = "grant";

    private transient AgentListener nectarAgentListener;

    public transient final MasterProvisioner masterProvisioner = new MasterProvisioner();

    private final Map<String, String> masterProvisioningGrants = new ConcurrentHashMap<String, String>();

    public static class AgentProtocolListener extends MetaNectarAgentProtocol.Listener {
        private final MetaNectar metaNectar;

        public AgentProtocolListener(MetaNectar metaNectar) {
            this.metaNectar = metaNectar;
        }

        public URL getEndpoint() throws IOException {
            // TODO MetaNectar.this.getRootUrl() is returning null
            String url = metaNectar.getRootUrl();
            if (url == null) url = metaNectar.METANECTAR_ROOT_URL;
            return new URL(url);
        }

        public void onConnectingTo(URL address, X509Certificate identity, String organization, Map<String, String> properties) throws GeneralSecurityException, IOException {
            // locate matching jenkins server
            JenkinsServer server = metaNectar.getServerByIdentity(identity.getPublicKey());
            if (server!=null) {
                if (server.isApproved()) {
                    LOGGER.info("Master is approved: " + organization + " " + address);
                    return;
                } else {
                    throw new GracefulConnectionRefusalException("The master " + organization + " is not approved by the MetaNectar administrator");
                }
            }

            // TODO: define a mode where MetaNectar admin can disable such new registration
            server = metaNectar.createProject(JenkinsServer.class, Util.getDigestOf(new ByteArrayInputStream(identity.getPublicKey().getEncoded())));
            server.setServerUrl(address);
            server.setIdentity((RSAPublicKey)identity.getPublicKey());

            // Check if there is a grant for automatic registration
            if (properties.containsKey(GRANT_PROPERTY)) {
                final String receivedGrant = properties.get(GRANT_PROPERTY);

                final Map<String, String> masterProvisioningGrants = metaNectar.masterProvisioningGrants;
                if (masterProvisioningGrants.containsKey(organization)) {
                    String issuedGrant = masterProvisioningGrants.get(organization);
                    if (issuedGrant.equals(receivedGrant)) {
                        masterProvisioningGrants.remove(organization);
                        server.setApproved(true);
                        LOGGER.info("Valid grant received. Master is automatically approved: " + organization + " " + address);
                        return;
                    } else {
                        // Grant is not the same that was issued
                        throw new GeneralSecurityException("Invalid grant for master " + organization);
                    }
                } else {
                    // Grant was not issued for the organization
                    throw new GeneralSecurityException("No grant was issued for master " + organization);
                }
            }

            LOGGER.info("Master is not approved: "+ organization + " " + address);
            throw new GracefulConnectionRefusalException("This master is not yet approved by the MetaNectar administrator");
        }

        public void onConnectedTo(Channel channel, X509Certificate identity, String organization) throws IOException {
            JenkinsServer server = metaNectar.getServerByIdentity(identity.getPublicKey());
            if (server!=null) {
                server.setChannel(channel);
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

    public JenkinsServer getServerByIdentity(PublicKey identity) {
        for (JenkinsServer js : getItems(JenkinsServer.class))
            if (js.getIdentity().equals(identity))
                return js;
        return null;
    }

    /**
     * Sets up the initial view state.
     */
    @Override
    protected View createInitialView() {
        try {
            JenkinsServerListView lv = new JenkinsServerListView("All");
            lv.setColumns(Arrays.asList(
                    new StatusColumn(),
                    new JenkinsServerColumn()));
            return lv;

        } catch (IOException e) {
            // view doesn't save itself unless it's connected to the parent, which we don't do in this method.
            // so this never happens
            throw new AssertionError(e);
        }
    }

    /**
     * Registers a new Nectar instance to this MetaNectar.
     */
    public JenkinsServer doAddNectar(@QueryParameter URL url) throws IOException, HttpResponseException {
        checkPermission(ADMINISTER);

        final URLConnection con = url.openConnection();
        if (!(con instanceof HttpURLConnection))
            // otherwise this gives access to local files, etc.
            throw new Failure(url+ " is not a valid HTTP URL");

        final HttpURLConnection hcon = (HttpURLConnection) con;
        hcon.connect();
        if (hcon.getResponseCode()>=400) {
            StringWriter sw = new StringWriter();
            sw.write(url+ " couldn't be retrieved. Status code was "+hcon.getResponseCode()+"\n");
            IOUtils.copy(new InputStreamReader(hcon.getErrorStream()), sw); // TODO: use the right encoding
            throw new Failure(sw.toString(),true);
        }

        if (hcon.getHeaderField("X-Jenkins")==null)
            throw new Failure(url+ " is neither Jenkins nor Nectar --- there's no X-Jenkins header");

        RSAPublicKey key=null;
        if (!BYPASS_INSTANCE_AUTHENTICATION) {
            final String id = hcon.getHeaderField("X-Instance-Identity");
            if (id==null)
                throw new Failure(url+ " doesn't appear to have the MetaNectar connector plugin installed.");

            try {
                key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(new Base64Encoder().decode(id)));
            } catch (GeneralSecurityException e) {
                throw new IOException2("Failed to parse the identity: "+id,e);
            }
        }

        // Add a new server. Since this instance was manually added, it's fair to say
        // that act constitutes an approval
        final JenkinsServer server = createProject(JenkinsServer.class, Util.getDigestOf(url.toExternalForm()));
        server.setServerUrl(url);
        if (key!=null) {
            server.setIdentity(key);
            server.setApproved(true);
        }

        return server;
    }


    /**
     * Provision a new master and issue a grant for automatic approval.
     *
     */
    public void provisionMaster(MasterProvisioner.MasterProvisionListener ml, MasterProvisioningService mns,
                                String organization) {
        provisionMaster(ml, mns, organization, true);
    }

    /**
     * Provision a new masters.
     */
    public void provisionMaster(MasterProvisioner.MasterProvisionListener ml, MasterProvisioningService mns,
                                String organization, boolean grant) {
        Map<String, String> properties = new HashMap<String, String>();
        if (grant)
            properties.put(GRANT_PROPERTY, createGrantForMaster(organization));
        masterProvisioner.provision(ml, mns, organization, getRootUrlAsURL(), properties);
    }

    /**
     * Create a grant for a master to be provisioned automatically.
     */
    public String createGrantForMaster(String organization) {
        String grant = UUID.randomUUID().toString();
        masterProvisioningGrants.put(organization, grant);
        return grant;
    }

    //

    public static MetaNectar getInstance() {
        return (MetaNectar)Hudson.getInstance();
    }

    /**
     * If true, bypass the RSA-based instance ID authentication altogether.
     * Convenient for debugging.
     */
    public static boolean BYPASS_INSTANCE_AUTHENTICATION = Boolean.getBoolean(MetaNectar.class.getName()+".bypassInstanceAuthentication");
}