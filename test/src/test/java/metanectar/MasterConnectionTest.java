package metanectar;

import com.cloudbees.commons.metanectar.agent.Agent;
import com.cloudbees.commons.metanectar.agent.AgentStatusListener;
import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.remoting.Channel;
import hudson.tasks.Mailer;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;
import metanectar.model.MetaNectarPortRootAction;
import metanectar.provisioning.MasterProvisioningService;
import metanectar.test.MetaNectarTestCase;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public class MasterConnectionTest extends MetaNectarTestCase {
    private InstanceIdentity id;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Mailer.descriptor().setHudsonUrl(getURL().toExternalForm());
        id = InstanceIdentity.get();
    }


    class Client extends MetaNectarAgentProtocol.Listener {
        Channel channel;

        @Override
        public URL getEndpoint() throws IOException {
            return new URL("http://bogus.client/");
        }

        @Override
        public void onConnectingTo(URL address, X509Certificate identity, String organization, Map<String, String> properties) throws GeneralSecurityException, IOException {
        }

        @Override
        public void onConnectedTo(Channel channel, X509Certificate identity, String organization) throws IOException {
            this.channel = channel;
        }

        @Override
        public void onRefusal(MetaNectarAgentProtocol.GracefulConnectionRefusalException e) throws Exception {
            throw e;
        }

        @Override
        public void onError(Exception e) throws Exception {
            throw e;
        }
    }

    private class TestAgentProtocolListener extends MetaNectarAgentProtocol.Listener {

        private final MetaNectarAgentProtocol.Listener l;

        private final CountDownLatch onConnectedLatch;

        private final CountDownLatch onRefusalLatch;

        public TestAgentProtocolListener(MetaNectarAgentProtocol.Listener l, CountDownLatch onConnectedLatch, CountDownLatch onRefusalLatch) {
            this.l = l;
            this.onConnectedLatch = onConnectedLatch;
            this.onRefusalLatch = onRefusalLatch;
        }

        public URL getEndpoint() throws IOException {
            return l.getEndpoint();
        }

        public void onConnectingTo(URL address, X509Certificate identity, String organization, Map<String, String> properties) throws GeneralSecurityException, IOException {
            l.onConnectingTo(address, identity, organization, properties);
        }

        public void onConnectedTo(Channel channel, X509Certificate identity, String organization) throws IOException {
            l.onConnectedTo(channel, identity, organization);
            if (onConnectedLatch != null)
                onConnectedLatch.countDown();
        }

        @Override
        public void onRefusal(MetaNectarAgentProtocol.GracefulConnectionRefusalException e) throws Exception {
            if (onRefusalLatch != null)
                onRefusalLatch.countDown();

            l.onRefusal(e);
        }

        @Override
        public void onError(Exception e) throws Exception {
            l.onError(e);
        }
    }

    public void testConnectionWithGrant() throws Exception {
        CountDownLatch onEventCdl = new CountDownLatch(1);
        metaNectar.configureNectarAgentListener(new TestAgentProtocolListener(new MetaNectar.AgentProtocolListener(metaNectar), onEventCdl, onEventCdl));

        Client client = new Client();

        MasterServer ms = metaNectar.createMasterServer("org");
        ms.setPreProvisionState();
        ms.setProvisionCompletedState(metaNectar, metaNectar.getMetaNectarPortUrl());

        Map<String, String> properties = new HashMap<String, String>();
        properties.put(MasterProvisioningService.PROPERTY_PROVISION_GRANT_ID, ms.getGrantId());
        MetaNectarAgentProtocol.Outbound p = new MetaNectarAgentProtocol.Outbound(
                MetaNectarAgentProtocol.getInstanceIdentityCertificate(id, metaNectar), id.getPrivate(),
                "org",
                properties,
                client, null);

        Agent agent = new Agent(new AgentStatusListener.LoggerListener(LOGGER), null, p);
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", metaNectar.getNectarAgentListener().getPort());

        agent.connectOnce(serverAddress);

        assertNotNull(ms.getIdentity());
        assertNotNull(ms.getEndpoint());

        // this should create an unapproved Jenkins instance on the server
        ms = metaNectar.getMasterByIdentity(id.getPublic());
        assertNotNull(ms);
        assertTrue(ms.isApproved());
        assertNotNull(client.channel);

        // Wait for channel to be established on the server
        onEventCdl.await(1, TimeUnit.MINUTES);
        assertNotNull(ms.getChannel());

        // verify that we can talk to each other
        client.channel.setProperty("client","hello");
        assertEquals("hello", ms.getChannel().waitForRemoteProperty("client"));
    }

    public void testConnectionWithoutGrant() throws Exception {
        CountDownLatch onEventCdl = new CountDownLatch(1);
        metaNectar.configureNectarAgentListener(new TestAgentProtocolListener(new MetaNectar.AgentProtocolListener(metaNectar), onEventCdl, onEventCdl));

        Client client = new Client();

        MasterServer ms = metaNectar.createMasterServer("org");
        ms.setPreProvisionState();
        ms.setProvisionCompletedState(metaNectar, metaNectar.getMetaNectarPortUrl());

        Map<String, String> properties = new HashMap<String, String>();
        MetaNectarAgentProtocol.Outbound p = new MetaNectarAgentProtocol.Outbound(
                MetaNectarAgentProtocol.getInstanceIdentityCertificate(id, metaNectar), id.getPrivate(),
                "org",
                properties,
                client, null);

        Agent agent = new Agent(new AgentStatusListener.LoggerListener(LOGGER), null, p);
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", metaNectar.getNectarAgentListener().getPort());


        Exception e = null;
        try {
            agent.connectOnce(serverAddress);
        } catch (Exception _e) {
            e = _e;
        }

        assertNotNull(e);
        assertTrue(e instanceof MetaNectarAgentProtocol.GracefulConnectionRefusalException);
    }

    public void testMetaNectarPort() throws IOException, SAXException {
        Page wc = new WebClient().goTo("/" + MetaNectarPortRootAction.URL_NAME + "/", "");

        List l = wc.getWebResponse().getResponseHeaders();

        String s = wc.getWebResponse().getResponseHeaderValue("X-MetaNectar-Port");
        assertNotNull(s);
        Integer port = metaNectar.getNectarAgentListener().getPort();
        assertEquals(port, Integer.valueOf(s));
    }

    private static final Logger LOGGER = Logger.getLogger(MasterConnectionTest.class.getName());
}
