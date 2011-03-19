package metanectar;

import hudson.remoting.Channel;
import hudson.tasks.Mailer;
import metanectar.agent.Agent;
import metanectar.agent.AgentStatusListener.LoggerListener;
import metanectar.agent.MetaNectarAgentProtocol;
import metanectar.agent.MetaNectarAgentProtocol.GracefulConnectionRefusalException;
import metanectar.agent.MetaNectarAgentProtocol.Listener;
import metanectar.agent.MetaNectarAgentProtocol.Outbound;
import metanectar.model.JenkinsServer;
import metanectar.test.MetaNectarTestCase;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
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
        class Client extends Listener {
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
        }


        Client client = new Client();
        Outbound p = new Outbound(MetaNectarAgentProtocol.getInstanceIdentityCertificate(id,metaNectar), id.getPrivate(), client);

        Agent agent = new Agent(new LoggerListener(LOGGER), null, p);
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", metaNectar.getJenkinsAgentListener().getPort());

        JenkinsServer js = metaNectar.getServerByIdentity(id.getPublic());
        assertNull(js);

        try {
            agent.connectOnce(serverAddress);
            fail();
        } catch (GracefulConnectionRefusalException e) {
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
        assertNotNull(js.getChannel());

        // verify that we can talk to each other
        client.channel.setProperty("client","hello");
        assertEquals("hello",js.getChannel().waitForRemoteProperty("client"));
    }

    private static final Logger LOGGER = Logger.getLogger(MetaNectarTest.class.getName());
}
