package metanectar;

import com.cloudbees.commons.metanectar.agent.Agent;
import com.cloudbees.commons.metanectar.agent.AgentStatusListener;
import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.remoting.Channel;
import hudson.tasks.Mailer;
import metanectar.model.JenkinsServer;
import metanectar.test.MetaNectarTestCase;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public class MetaNectarTest extends MetaNectarTestCase {
    private InstanceIdentity id;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Mailer.descriptor().setHudsonUrl(getURL().toExternalForm());
        id = InstanceIdentity.get();
    }

    public void testConnection() throws Exception {
        class Client extends MetaNectarAgentProtocol.Listener {
            Channel channel;
            @Override
            public URL getOurURL() throws IOException {
                return new URL("http://bogus.client/");
            }

            @Override
            public void onConnectingTo(URL address, X509Certificate identity) throws GeneralSecurityException, IOException {
            }

            @Override
            public void onConnectedTo(Channel channel, X509Certificate identity) throws IOException {
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


        Client client = new Client();
        MetaNectarAgentProtocol.Outbound p = new MetaNectarAgentProtocol.Outbound(MetaNectarAgentProtocol.getInstanceIdentityCertificate(id,metaNectar), id.getPrivate(), client, null);

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
        // Sleep a little to wait for channel to be set on the remote side
        // TODO we should change this to use some sort of co-ordination
        Thread.currentThread().sleep(100);
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

    private static final Logger LOGGER = Logger.getLogger(MetaNectarTest.class.getName());
}
