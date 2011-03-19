package metanectar.model;

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
import metanectar.agent.AgentListener;
import metanectar.agent.AgentStatusListener;
import metanectar.agent.MetaNectarAgentProtocol;
import metanectar.agent.MetaNectarAgentProtocol.GracefulConnectionRefusalException;
import metanectar.agent.MetaNectarAgentProtocol.Listener;
import metanectar.model.views.JenkinsServerColumn;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jvnet.hudson.reactor.ReactorException;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;

/**
 * The root object of MetaNectar.
 *
 * Extends from {@link Hudson} to keep existing code working.
 *
 * @author Paul Sandoz
 * @author Kohsuke Kawaguchi
 */
public class MetaNectar extends Hudson {

    protected transient AgentListener jenkinsAgentListener;

    public MetaNectar(File root, ServletContext context) throws IOException, InterruptedException, ReactorException {
        this(root, context, null);
    }

    public MetaNectar(File root, ServletContext context, PluginManager pluginManager) throws IOException, InterruptedException, ReactorException {
        super(root, context, pluginManager);

        {
            AgentStatusListener asl = new AgentStatusListener() {
                public void status(String msg) {
                }

                public void status(String msg, Throwable t) {
                }

                public void error(Throwable t) {
                }
            };

            InstanceIdentity id = InstanceIdentity.get();
            MetaNectarAgentProtocol.Inbound p = new MetaNectarAgentProtocol.Inbound(
                    MetaNectarAgentProtocol.getInstanceIdentityCertificate(id, this),
                    id.getPrivate(), new Listener() {
                        public URL getOurURL() throws IOException {
                            return new URL(MetaNectar.this.getRootUrl());
                        }

                        public void onConnectingTo(URL address, X509Certificate identity) throws GeneralSecurityException, IOException {
                            // locate matching jenkins server
                            JenkinsServer js = getServerByIdentity(identity.getPublicKey());
                            if (js!=null) {
                                if (js.isApproved())
                                    return;
                                else
                                    throw new GracefulConnectionRefusalException("This master is not yet approved by the MetaNectar administrator");
                            }

                            // none found. add an unapproved server, and reject it for now
                            // TODO: define a mode where MetaNectar admin can disable such new registration
                            final JenkinsServer server = createProject(JenkinsServer.class, Util.getDigestOf(new ByteArrayInputStream(identity.getPublicKey().getEncoded())));
                            server.setServerUrl(address);
                            server.setIdentity((RSAPublicKey)identity.getPublicKey());

                            throw new GracefulConnectionRefusalException("This master is not yet approved by the MetaNectar administrator");
                        }

                        public void onConnectedTo(Channel channel, X509Certificate identity) throws IOException {
                            JenkinsServer js = getServerByIdentity(identity.getPublicKey());
                            if (js!=null) {
                                js.setChannel(channel);
                                return;
                            }
                            
                            channel.close();
                            throw new IOException("Unable to route the connection. No server found");
                        }
                    });


            // TODO choose port
            try {
                jenkinsAgentListener = new AgentListener(asl, 0, Collections.singletonList(p));
                new Thread(jenkinsAgentListener, "TCP slave agent listener port=" + 0).start();
            } catch (BindException e) {
                new AdministrativeError(getClass().getName()+".tcpBind",
                        "Failed to listen to incoming agent connection",
                        "Failed to listen to incoming agent connection. <a href='configure'>Change the port number</a> to solve the problem.",e);
            }
        }
    }

    public AgentListener getJenkinsAgentListener() {
        return jenkinsAgentListener;
    }

    @Override
    public void cleanUp() {
        super.cleanUp();

        if(jenkinsAgentListener != null)
            jenkinsAgentListener.shutdown();
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

    public static MetaNectar getInstance() {
        return (MetaNectar)Hudson.getInstance();
    }

    /**
     * If true, bypass the RSA-based instance ID authentication altogether.
     * Convenient for debugging.
     */
    public static boolean BYPASS_INSTANCE_AUTHENTICATION = Boolean.getBoolean(MetaNectar.class.getName()+".bypassInstanceAuthentication");
}