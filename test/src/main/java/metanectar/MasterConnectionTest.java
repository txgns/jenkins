package metanectar;

import com.cloudbees.commons.metanectar.agent.Agent;
import com.cloudbees.commons.metanectar.agent.AgentStatusListener;
import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.remoting.Channel;
import hudson.tasks.Mailer;
import metanectar.model.JenkinsServer;
import metanectar.model.MetaNectar;
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
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(MetaNectar.GRANT_PROPERTY, metaNectar.createGrantForMaster("org"));
        MetaNectarAgentProtocol.Outbound p = new MetaNectarAgentProtocol.Outbound(
                MetaNectarAgentProtocol.getInstanceIdentityCertificate(id,metaNectar), id.getPrivate(),
                "org",
                properties,
                client, null);

        Agent agent = new Agent(new AgentStatusListener.LoggerListener(LOGGER), null, p);
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", metaNectar.getNectarAgentListener().getPort());

        JenkinsServer js = metaNectar.getServerByIdentity(id.getPublic());
        assertNull(js);

        agent.connectOnce(serverAddress);

        // this should create an unapproved Jenkins instance on the server
        js = metaNectar.getServerByIdentity(id.getPublic());
        assertNotNull(js);
        assertTrue(js.isApproved());
        assertNotNull(client.channel);

        // Wait for channel to be established on the server
        onEventCdl.await(1, TimeUnit.MINUTES);
        assertNotNull(js.getChannel());

        // verify that we can talk to each other
        client.channel.setProperty("client","hello");
        assertEquals("hello",js.getChannel().waitForRemoteProperty("client"));
    }

    public void testConnectionWithApproval() throws Exception {
        CountDownLatch onEventCdl = new CountDownLatch(2);
        metaNectar.configureNectarAgentListener(new TestAgentProtocolListener(new MetaNectar.AgentProtocolListener(metaNectar), onEventCdl, onEventCdl));

        Client client = new Client();
        MetaNectarAgentProtocol.Outbound p = new MetaNectarAgentProtocol.Outbound(
                MetaNectarAgentProtocol.getInstanceIdentityCertificate(id,metaNectar), id.getPrivate(),
                "org",
                Collections.<String, String>emptyMap(),
                client, null);

        Agent agent = new Agent(new AgentStatusListener.LoggerListener(LOGGER), null, p);
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", metaNectar.getNectarAgentListener().getPort());

        JenkinsServer js = metaNectar.getServerByIdentity(id.getPublic());
        assertNull(js);

        try {
            agent.connectOnce(serverAddress);
            fail();
        } catch (MetaNectarAgentProtocol.GracefulConnectionRefusalException e) {
            // we aren't approved yet
        }

        // this should create an unapproved Jenkins instance on the server
        js = metaNectar.getServerByIdentity(id.getPublic());
        assertNotNull(js);
        assertFalse(js.isApproved());

        // approve it, and reconnect
        js.setApproved(true);
        agent.connectOnce(serverAddress);

        // this should succeed, and the channel should be established
        assertNotNull(client.channel);

        // Wait for channel to be established on the server
        onEventCdl.await(1, TimeUnit.MINUTES);
        assertNotNull(js.getChannel());

        // verify that we can talk to each other
        client.channel.setProperty("client","hello");
        assertEquals("hello",js.getChannel().waitForRemoteProperty("client"));
    }


    public void testAgentListenerPort() throws IOException, SAXException {
        HtmlPage wc = new WebClient().goTo("/");

        List l = wc.getWebResponse().getResponseHeaders();

        String s = wc.getWebResponse().getResponseHeaderValue("X-MetaNectar-Port");
        assertNotNull(s);
        Integer port = metaNectar.getNectarAgentListener().getPort();
        assertEquals(port, Integer.valueOf(s));
    }

    private static final Logger LOGGER = Logger.getLogger(MasterConnectionTest.class.getName());
}
