package metanectar.provisioning;

import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import hudson.remoting.Channel;
import metanectar.cloud.MasterProvisioningCloud;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static metanectar.provisioning.LatchMasterServerListener.ProvisionListener;
import static metanectar.provisioning.LatchMasterServerListener.TerminateListener;


/**
 * @author Paul Sandoz
 */
public class MasterProvisioningConnectionTest extends AbstractMasterProvisioningTest {

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

    public void _testProvision(int masters) throws Exception {
        _testProvision(masters, new Configurable() {
            public void configure() throws Exception {
                SlaveMasterProvisioningNodePropertyTemplate tp = new SlaveMasterProvisioningNodePropertyTemplate(4, new TestMasterProvisioningService(100));
                MasterProvisioningCloud pc = new MasterProvisioningCloud(tp, new TestSlaveCloud(MasterProvisioningConnectionTest.this, 100));
                metaNectar.clouds.add(pc);
            }
        });
    }

    public void testProvisionOneMasterOnMetaNectar() throws Exception {
        _testProvisionOnMetaNectar(1);
    }

    public void _testProvisionOnMetaNectar(int masters) throws Exception {
        _testProvision(masters, new Configurable() {
            public void configure() throws Exception {
                TestMasterProvisioningService s = new TestMasterProvisioningService(100);
                metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(4, s));
                // Reset the labels
                metaNectar.setNodes(metaNectar.getNodes());
            }
        });
    }

    interface Configurable {
        void configure() throws Exception;
    }

    public void _testProvision(int masters, Configurable c) throws Exception {
        new WebClient().goTo("/");

        CountDownLatch onEventCdl = new CountDownLatch(masters);
        metaNectar.configureNectarAgentListener(new TestAgentProtocolListener(new MetaNectar.AgentProtocolListener(metaNectar), onEventCdl, onEventCdl));

        c.configure();

        ProvisionListener pl = new ProvisionListener(2 * masters);

        for (int i = 0; i < masters; i++) {
            MasterServer ms = metaNectar.createMasterServer("org" + i);
            metaNectar.provisionMaster(ms);
        }

        // Wait for masters to be provisioned
        pl.await(1, TimeUnit.MINUTES);

        // Wait for masters to be connected
        onEventCdl.await(1, TimeUnit.MINUTES);

        assertEquals(masters, metaNectar.getItems(MasterServer.class).size());
        for (MasterServer ms : metaNectar.getItems(MasterServer.class)) {
            assertTrue(ms.isApproved());
            assertNotNull(ms.getChannel());
        }
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
        _testProvision(masters);
        _testDelete(masters);
    }

    private void _testDelete(int masters) throws Exception {
        TerminateListener tl = new TerminateListener(2 * masters);

        for (int i = 0; i < masters; i++) {
            metaNectar.masterProvisioner.terminate(metaNectar.getMasterByOrganization("org" + i), true);
        }

        tl.await(1, TimeUnit.MINUTES);


        assertEquals(masters, metaNectar.getItems(MasterServer.class).size());
        for (MasterServer ms : metaNectar.getItems(MasterServer.class)) {
            assertEquals(MasterServer.State.Terminated, ms.getState());
            assertNull(ms.getChannel());
        }

        // TODO when node terminate is implemented need to assert that there are no nodes
//        assertEquals(nodes, MyComputerListener.get().online.size());
//        assertEquals(nodes, metaNectar.masterProvisioner.masterLabel.getNodes().size());

        assertEquals(0, MasterProvisioner.getProvisionedMasters(metaNectar).size());
    }

}
