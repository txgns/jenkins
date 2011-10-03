package metanectar.provisioning;

import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import com.google.common.collect.Lists;
import hudson.model.LoadStatistics;
import hudson.remoting.Channel;
import metanectar.cloud.MasterProvisioningCloudProxy;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static metanectar.provisioning.LatchMasterProvisioningCloudListener.TerminateListener;
import static metanectar.provisioning.LatchMasterServerListener.ProvisionAndStartListener;
import static metanectar.provisioning.LatchMasterServerListener.StopAndTerminateListener;


/**
 * @author Paul Sandoz
 */
public class MasterProvisioningConnectionTest extends AbstractMasterProvisioningTestCase {

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

    @Override
    protected void tearDown() throws Exception {
        if (s != null) {
            s.stopAll();
        }

        super.tearDown();
    }

    public void testProvisionOneMaster() throws Exception {
        _testProvision(1);
    }

    public void testProvisionTwoMaster() throws Exception {
        _testProvision(2);
    }

    public void testProvisionFourMaster() throws Exception {
        _testProvision(4);
    }

    public void testProvisionEightMaster() throws Exception {
        _testProvision(8);
    }

    public List<MasterServer> _testProvision(int masters) throws Exception {
        return _testProvision(masters, new Configurable() {
            public void configure() throws Exception {
                MasterProvisioningNodePropertyTemplate tp = new MasterProvisioningNodePropertyTemplate(4, new TestMasterProvisioningService(100));
                MasterProvisioningCloudProxy pc = new MasterProvisioningCloudProxy(tp, new TestSlaveCloud(MasterProvisioningConnectionTest.this, 100));
                metaNectar.clouds.add(pc);
            }
        });
    }

    public void testProvisionOneMasterOnMetaNectar() throws Exception {
        _testProvisionOnMetaNectar(1);
    }

    private TestMasterProvisioningService s;

    public MasterServer _testProvisionOnMetaNectar(int masters) throws Exception {
        return _testProvision(masters, new Configurable() {
            public void configure() throws Exception {
                s = new TestMasterProvisioningService(100);
                metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(4, s));
            }
        }).get(0);
    }

    interface Configurable {
        void configure() throws Exception;
    }

    public List<MasterServer> _testProvision(int masters, Configurable c) throws Exception {
        new WebClient().goTo("/");

        CountDownLatch onEventCdl = new CountDownLatch(masters);
        metaNectar.configureNectarAgentListener(new TestAgentProtocolListener(new MetaNectar.AgentProtocolListener(metaNectar), onEventCdl, onEventCdl));

        c.configure();

        ProvisionAndStartListener pl = new ProvisionAndStartListener(4 * masters);

        List<MasterServer> l = Lists.newArrayList();
        for (int i = 0; i < masters; i++) {
            MasterServer ms = metaNectar.createManagedMaster("org" + i);
            l.add(ms);
            ms.provisionAndStartAction();
        }

        // Wait for masters to be provisioned
        pl.await(1, TimeUnit.MINUTES);

        // Wait for masters to be connected
        onEventCdl.await(1, TimeUnit.MINUTES);

        assertEquals(masters, metaNectar.getManagedMasters().size());
        for (MasterServer mn : metaNectar.getManagedMasters()) {
            assertEquals(mn, metaNectar.getManagedMasterByName(mn.getIdName()));
            assertTrue(mn.isApproved());
            assertNotNull(mn.getChannel());
        }

        return l;
    }


    public void testDeleteOneMaster() throws Exception {
        _testProvisionAndDelete(1);
    }

    public void testDeleteTwoMaster() throws Exception {
        _testProvisionAndDelete(2);
    }

    public void testDeleteFourMaster() throws Exception {
        _testProvisionAndDelete(4);
    }

    public void testDeleteEightMaster() throws Exception {
        _testProvisionAndDelete(8);
    }

    private void _testProvisionAndDelete(int masters) throws Exception {
        _testDelete(_testProvision(masters));
    }

    private void _testDelete(List<MasterServer> masters) throws Exception {
        int nodes = masters.size() / 4 + Math.min(masters.size() % 4, 1);

        TerminateListener cloudTl = new TerminateListener(nodes);
        StopAndTerminateListener masterTl = new StopAndTerminateListener(4 * masters.size());

        for (MasterServer mn : masters) {
            mn.stopAndTerminateAction();
        }

        masterTl.await(1, TimeUnit.MINUTES);

        assertEquals(masters.size(), metaNectar.getManagedMasters().size());
        for (MasterServer ms : metaNectar.getManagedMasters()) {
            assertEquals(MasterServer.State.Terminated, ms.getState());
            assertNull(ms.getChannel());
        }

        cloudTl.await(1, TimeUnit.MINUTES);

        assertEquals(0, metaNectar.masterProvisioner.getProvisionedMasters().keySet().size());
        assertEquals(0, metaNectar.masterProvisioner.getProvisionedMasters().size());
    }

}
