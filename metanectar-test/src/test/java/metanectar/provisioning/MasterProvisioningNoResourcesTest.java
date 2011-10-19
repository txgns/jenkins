package metanectar.provisioning;

import metanectar.LatchConnectedMasterListener;
import metanectar.model.MasterServer;

import java.util.concurrent.TimeUnit;

/**
 * @author Paul Sandoz
 */
public class MasterProvisioningNoResourcesTest extends AbstractMasterProvisioningTestCase {

    public void testProvisionOneMasterOnMetaNectar() throws Exception {
        new WebClient().goTo("/");

        MasterServer ms = metaNectar.createManagedMaster("org");

        LatchMasterServerListener noResources = new LatchMasterServerListener(1) {
            public void onProvisioningErrorNoResources(MasterServer ms) {
                countDown();
            }
        };

        LatchMasterServerListener approved = new LatchMasterServerListener.ProvisionListener(3) {
            public void onApproved(MasterServer ms) {
                countDown();
            }
        };

        LatchConnectedMasterListener connected = new LatchConnectedMasterListener.ConnectedListener(1);

        // Try to provision when there are no resources
        ms.provisionAndStartAction();

        noResources.await(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.ProvisioningErrorNoResources, ms.getState());
        assertEquals(3, approved.getCount());
        assertEquals(1, connected.getCount());

        // Add provisioning resources
        metanectar.provisioning.DummyMasterProvisioningService s = new metanectar.provisioning.DummyMasterProvisioningService(100);
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(4, s));

        approved.await(1, TimeUnit.MINUTES);
        connected.await(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.Approved, ms.getState());

        s.stopAll();
    }

    public void testProvisionCancel() throws Exception {
        new WebClient().goTo("/");

        MasterServer ms = metaNectar.createManagedMaster("org");

        LatchMasterServerListener noResources = new LatchMasterServerListener(1) {
            public void onProvisioningErrorNoResources(MasterServer ms) {
                countDown();
            }
        };

        // Try to provision when there are no resources
        ms.provisionAndStartAction();

        noResources.await(1, TimeUnit.MINUTES);

        // Cancel the request
        assertTrue(ms.cancelProvisionAction());

        assertFalse(metaNectar.masterProvisioner.hasPendingRequests());

        assertFalse(metaNectar.masterProvisioner.cancelPendingRequest(ms));
    }

}
