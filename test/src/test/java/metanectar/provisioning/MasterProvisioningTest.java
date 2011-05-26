package metanectar.provisioning;

import hudson.model.Node;
import hudson.slaves.Cloud;
import metanectar.cloud.MasterProvisioningCloudListener;
import metanectar.cloud.MasterProvisioningCloudProxy;
import metanectar.model.MasterServer;

import java.util.concurrent.TimeUnit;

import static metanectar.provisioning.LatchMasterProvisioningCloudListener.TerminateListener;
import static metanectar.provisioning.LatchMasterServerListener.ProvisionAndStartListener;
import static metanectar.provisioning.LatchMasterServerListener.StopAndTerminateListener;

/**
 * @author Paul Sandoz
 */
public class MasterProvisioningTest extends AbstractMasterProvisioningTestCase {

    public static class ProvisionListener extends MasterProvisioningCloudListener {
        int provisioned;

        public ProvisionListener() {
            MasterProvisioningCloudListener.all().add(0, this);
        }

        public void onProvisioned(Cloud c, Node n) {
            provisioned++;
        }
    }

    public void testProvisionOneMaster() throws Exception {
        _testProvision(1, 4);
    }

    public void testProvisionTwoMaster() throws Exception {
        _testProvision(2, 4);
    }

    public void testProvisionFourMaster() throws Exception {
        _testProvision(4, 4);
    }

    public void testProvisionSixMaster() throws Exception {
        _testProvision(6, 4);
    }

    public void testProvisionEightMaster() throws Exception {
        _testProvision(8, 4);
    }

    private void _testProvision(int masters, int nodesPerMaster) throws Exception {
        int nodes = masters / nodesPerMaster + Math.min(masters % nodesPerMaster, 1);

        ProvisionListener cl = new ProvisionListener();

        SlaveMasterProvisioningNodePropertyTemplate tp = new SlaveMasterProvisioningNodePropertyTemplate(nodesPerMaster, new DummyMasterProvisioningService(100));
        MasterProvisioningCloudProxy pc = new MasterProvisioningCloudProxy(tp, new TestSlaveCloud(this, 100));
        metaNectar.clouds.add(pc);

        ProvisionAndStartListener pl = new ProvisionAndStartListener(4 * masters);

        for (int i = 0; i < masters; i++) {
            MasterServer ms = metaNectar.createMasterServer("org" + i);
            ms.provisionAndStartAction();
        }

        pl.await(1, TimeUnit.MINUTES);

        assertEquals(nodes, cl.provisioned);
        assertEquals(nodes, metaNectar.masterProvisioner.getLabel().getNodes().size());
        assertEquals(masters, MasterProvisioner.getProvisionedMasters(metaNectar).size());
    }


    public void testDeleteOneMaster() throws Exception {
        _testDelete(1);
    }

    public void testDeleteTwoMaster() throws Exception {
        _testDelete(2);
    }

    public void testDeleteFourMaster() throws Exception {
        _testDelete(4);
    }

    public void testDeleteSixMaster() throws Exception {
        _testDelete(6);
    }

    public void testDeleteEightMaster() throws Exception {
        _testDelete(8);
    }

    private void _testDelete(int masters) throws Exception {
        _testProvision(masters, 4);
        _testDelete(masters, 4);
    }

    private void _testDelete(int masters, int nodesPerMaster) throws Exception {
        int nodes = masters / nodesPerMaster + Math.min(masters % nodesPerMaster, 1);

        TerminateListener cloudTl = new TerminateListener(nodes);
        StopAndTerminateListener masterTl = new StopAndTerminateListener(4 * masters);

        for (int i = 0; i < masters; i++) {
            metaNectar.getMasterByName("org" + i).stopAndTerminateAction(true);
        }

        masterTl.await(1, TimeUnit.MINUTES);

        cloudTl.await(1, TimeUnit.MINUTES);

        assertEquals(0, metaNectar.masterProvisioner.getLabel().getNodes().size());
        assertEquals(0, MasterProvisioner.getProvisionedMasters(metaNectar).size());
    }


    public void testProvisionOneMasterOnMetaNectarNode() throws Exception {
        _testProvisionOnMetaNectarNode(1, 4);
    }

    private void _testProvisionOnMetaNectarNode(int masters, int nodesPerMaster) throws Exception {
        int nodes = masters / nodesPerMaster + Math.min(masters % nodesPerMaster, 1);

        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(nodesPerMaster, new DummyMasterProvisioningService(100)));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        ProvisionAndStartListener pl = new ProvisionAndStartListener(4 * masters);

        for (int i = 0; i < masters; i++) {
            MasterServer ms = metaNectar.createMasterServer("org" + i);
            ms.provisionAndStartAction();
        }

        pl.await(1, TimeUnit.MINUTES);

        assertEquals(nodes, metaNectar.masterProvisioner.getLabel().getNodes().size());
        assertEquals(masters, MasterProvisioner.getProvisionedMasters(metaNectar).size());
    }

    public void testDeleteOneMasterOnMetaNectarNode() throws Exception {
        _testDeleteOnMetaNectarNode(1);
    }

    private void _testDeleteOnMetaNectarNode(int masters) throws Exception {
        _testProvisionOnMetaNectarNode(masters, 4);
        _testDeleteOnMetaNectarNode(masters, 4);
    }

    private void _testDeleteOnMetaNectarNode(int masters, int nodesPerMaster) throws Exception {
        int nodes = masters / nodesPerMaster + Math.min(masters % nodesPerMaster, 1);

        StopAndTerminateListener tl = new StopAndTerminateListener(4 * masters);

        for (int i = 0; i < masters; i++) {
            metaNectar.getMasterByName("org" + i).stopAndTerminateAction(true);
        }

        tl.await(1, TimeUnit.MINUTES);

        assertEquals(0, MasterProvisioner.getProvisionedMasters(metaNectar).size());
    }


    public void testOrdinal1() throws Exception {
        configureDummyMasterProvisioningOnMetaNectar();

        MasterServer ms0 = provisionAndStartMaster("0");
        assertEquals(0, ms0.getId());

        terminateAndDeleteMaster(ms0);

        ms0 = provisionAndStartMaster("0");
        assertEquals(0, ms0.getId());
    }

    public void testOrdinal2() throws Exception {
        configureDummyMasterProvisioningOnMetaNectar();

        MasterServer ms0 = provisionAndStartMaster("0");
        assertEquals(0, ms0.getId());

        MasterServer ms1 = provisionAndStartMaster("1");
        assertEquals(1, ms1.getId());

        terminateAndDeleteMaster(ms0);

        ms0 = provisionAndStartMaster("0");
        assertEquals(0, ms0.getId());

        terminateAndDeleteMaster(ms1);

        ms1 = provisionAndStartMaster("1");
        assertEquals(1, ms1.getId());
    }

    public void testOrdinal4() throws Exception {
        configureDummyMasterProvisioningOnMetaNectar();

        MasterServer ms0 = provisionAndStartMaster("0");
        assertEquals(0, ms0.getId());

        MasterServer ms1 = provisionAndStartMaster("1");
        assertEquals(1, ms1.getId());

        MasterServer ms2 = provisionAndStartMaster("2");
        assertEquals(2, ms2.getId());

        MasterServer ms3 = provisionAndStartMaster("3");
        assertEquals(3, ms3.getId());

        terminateAndDeleteMaster(ms1);
        terminateAndDeleteMaster(ms2);

        ms1 = provisionAndStartMaster("1");
        assertEquals(1, ms1.getId());

        ms2 = provisionAndStartMaster("2");
        assertEquals(2, ms2.getId());

        terminateAndDeleteMaster(ms0);
        terminateAndDeleteMaster(ms1);
        terminateAndDeleteMaster(ms2);

        ms0 = provisionAndStartMaster("0");
        assertEquals(0, ms0.getId());

        ms1 = provisionAndStartMaster("1");
        assertEquals(1, ms1.getId());

        ms2 = provisionAndStartMaster("2");
        assertEquals(2, ms2.getId());

    }
}
