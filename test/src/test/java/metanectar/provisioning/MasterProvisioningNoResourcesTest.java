package metanectar.provisioning;

import metanectar.model.MasterServer;

import java.util.concurrent.TimeUnit;

/**
 * @author Paul Sandoz
 */
public class MasterProvisioningNoResourcesTest extends AbstractMasterProvisioningTest {

    public void testProvisionOneMasterOnMetaNectar() throws Exception {
        new WebClient().goTo("/");

        MasterServer ms = metaNectar.createMasterServer("org");

        LatchMasterServerListener noResources = new LatchMasterServerListener(1) {
            public void onProvisioningErrorNoResources(MasterServer ms) {
                countDown();
            }
        };

        LatchMasterServerListener connected = new LatchMasterServerListener.ProvisionListener(4) {
            public void onApproved(MasterServer ms) {
                countDown();
            }

            public void onConnected(MasterServer ms) {
                countDown();
            }
        };

        // Try to provision when there are no resources
        metaNectar.provisionMaster(ms);

        noResources.await(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.ProvisioningErrorNoResources, ms.getState());
        assertEquals(4, connected.getCount());

        // Add provisioning resources
        TestMasterProvisioningService s = new TestMasterProvisioningService(100);
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(4, s));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        connected.await(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.Connectable, ms.getState());
    }
}
