package metanectar;

import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import com.google.common.collect.Maps;
import hudson.remoting.Channel;
import metanectar.model.AttachedMaster;
import metanectar.model.MetaNectar;
import metanectar.provisioning.MasterProvisioningService;
import metanectar.provisioning.DummyMasterServer;
import metanectar.test.MetaNectarTestCase;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Paul Sandoz
 */
public class AttachedMasterTest extends MetaNectarTestCase {

    public void testAttached() throws Exception {
        LatchConnectedMasterListener connected = new LatchConnectedMasterListener.ConnectedListener(1);

        AttachedMaster am = metaNectar.createAttachedMaster("o1");

        final Map<String, Object> properties = Maps.newHashMap();
        properties.put(MasterProvisioningService.PROPERTY_PROVISION_GRANT_ID, am.getGrantId());
        DummyMasterServer server = new DummyMasterServer(metaNectar.getMetaNectarPortUrl(), am.getIdName(), properties);

        server.start();

        connected.await(1, TimeUnit.MINUTES);
        assertEquals(AttachedMaster.State.Approved, am.getState());
        assertTrue(am.isOnline());

        LatchConnectedMasterListener disconnected = new LatchConnectedMasterListener.DisconnectedListener(1);

        server.stop();

        disconnected.await(1, TimeUnit.MINUTES);
        assertEquals(AttachedMaster.State.Approved, am.getState());
        assertTrue(am.isOffline());
    }

    // TODO test invalid name

    // TODO test ivalid instance ID


    private class TestAgentProtocolListener extends MetaNectarAgentProtocol.Listener {

        private final MetaNectarAgentProtocol.Listener l;

        private final CountDownLatch onConnectedLatch;

        private final CountDownLatch onRefusalLatch;

        private final CountDownLatch onErrorLatch;

        public TestAgentProtocolListener(MetaNectarAgentProtocol.Listener l, CountDownLatch onConnectedLatch, CountDownLatch onRefusalLatch, CountDownLatch onErrorLatch) {
            this.l = l;
            this.onConnectedLatch = onConnectedLatch;
            this.onRefusalLatch = onRefusalLatch;
            this.onErrorLatch = onErrorLatch;
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
            if (onErrorLatch != null)
                onErrorLatch.countDown();

            l.onError(e);
        }
    }

    public void testUnknownMaster() throws Exception {
        CountDownLatch onEventCdl = new CountDownLatch(1);
        metaNectar.configureNectarAgentListener(new TestAgentProtocolListener(new MetaNectar.AgentProtocolListener(metaNectar), null, null, onEventCdl));

        final Map<String, Object> properties = Maps.newHashMap();
        properties.put(MasterProvisioningService.PROPERTY_PROVISION_GRANT_ID, "1234");
        DummyMasterServer server = new DummyMasterServer(metaNectar.getMetaNectarPortUrl(), "foo", properties);

        server.start();

        assertTrue(onEventCdl.await(1, TimeUnit.MINUTES));

        server.stop();
    }

    public void testExistingMaster() throws Exception {
        AttachedMaster am = metaNectar.createAttachedMaster("o1");

        {
            LatchConnectedMasterListener connected = new LatchConnectedMasterListener.ConnectedListener(1);

            final Map<String, Object> properties = Maps.newHashMap();
            properties.put(MasterProvisioningService.PROPERTY_PROVISION_GRANT_ID, am.getGrantId());
            DummyMasterServer server = new DummyMasterServer(metaNectar.getMetaNectarPortUrl(), am.getIdName(), properties);

            server.start();

            connected.await(1, TimeUnit.MINUTES);
            assertEquals(AttachedMaster.State.Approved, am.getState());
            assertTrue(am.isOnline());
        }

        CountDownLatch onEventCdl = new CountDownLatch(1);
        metaNectar.configureNectarAgentListener(new TestAgentProtocolListener(new MetaNectar.AgentProtocolListener(metaNectar), null, null, onEventCdl));

        final Map<String, Object> properties = Maps.newHashMap();
        DummyMasterServer server = new DummyMasterServer(metaNectar.getMetaNectarPortUrl(), am.getIdName(), properties);

        server.start();

        assertTrue(onEventCdl.await(1, TimeUnit.MINUTES));

        server.stop();
    }

    public void testBadGrantId() throws Exception {
        CountDownLatch onEventCdl = new CountDownLatch(1);
        metaNectar.configureNectarAgentListener(new TestAgentProtocolListener(new MetaNectar.AgentProtocolListener(metaNectar), null, null, onEventCdl));

        AttachedMaster am = metaNectar.createAttachedMaster("o1");

        final Map<String, Object> properties = Maps.newHashMap();
        properties.put(MasterProvisioningService.PROPERTY_PROVISION_GRANT_ID, "1234");
        DummyMasterServer server = new DummyMasterServer(metaNectar.getMetaNectarPortUrl(), am.getIdName(), properties);

        server.start();

        assertTrue(onEventCdl.await(1, TimeUnit.MINUTES));
        assertTrue(GeneralSecurityException.class.isInstance(am.getError()));

        server.stop();
    }

    public void testRefused() throws Exception {
        CountDownLatch onEventCdl = new CountDownLatch(1);
        metaNectar.configureNectarAgentListener(new TestAgentProtocolListener(new MetaNectar.AgentProtocolListener(metaNectar), null, null, onEventCdl));

        AttachedMaster am = metaNectar.createAttachedMaster("o1");

        final Map<String, Object> properties = Maps.newHashMap();
        DummyMasterServer server = new DummyMasterServer(metaNectar.getMetaNectarPortUrl(), am.getIdName(), properties);

        server.start();

        assertTrue(onEventCdl.await(1, TimeUnit.MINUTES));
        assertTrue(MetaNectarAgentProtocol.GracefulConnectionRefusalException.class.isInstance(am.getError()));

        server.stop();
    }
}
