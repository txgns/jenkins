package metanectar.provisioning;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import metanectar.cloud.MasterProvisioningCloud;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static metanectar.provisioning.LatchMasterServerListener.ProvisionListener;
import static metanectar.provisioning.LatchMasterServerListener.TerminateListener;

/**
 * @author Paul Sandoz
 */
public class MasterProvisioningTest extends AbstractMasterProvisioningTest {

    public static class Service extends MasterProvisioningService {

        private final int delay;

        Service(int delay) {
            this.delay = delay;
        }

        public Future<Master> provision(VirtualChannel channel, TaskListener listener,
                                        int id, final String organization, final URL metaNectarEndpoint, Map<String, Object> properties) throws IOException, InterruptedException {
            return Computer.threadPoolForRemoting.submit(new Callable<Master>() {
                public Master call() throws Exception {
                    Thread.sleep(delay);

                    System.out.println("launching master");

                    return new Master(organization, metaNectarEndpoint);
                }
            });
        }

        public Future<?> terminate(VirtualChannel channel, TaskListener listener,
                                   String organization, boolean clean) throws IOException, InterruptedException {
            return Computer.threadPoolForRemoting.submit(new Callable<Void>() {
                public Void call() throws Exception {
                    Thread.sleep(delay);

                    System.out.println("deleting master");

                    return null;
                }
            });
        }
    }

    public static class MyComputerListener extends ComputerListener {

        Set<Node> online = new HashSet<Node>();

        public MyComputerListener() {
            ComputerListener.all().add(0, this);
        }

        public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
            if (MetaNectar.getInstance().masterProvisioner != null)
                online.add(c.getNode());
        }

        public void onOffline(Computer c) {
            if (MetaNectar.getInstance().masterProvisioner != null)
                online.remove(c.getNode());
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

        MyComputerListener cl = new MyComputerListener();

        MasterProvisioningNodeProperty p = new MasterProvisioningNodeProperty(nodesPerMaster, new TestMasterProvisioningService(100));
        MasterProvisioningCloud pc = new MasterProvisioningCloud(p, new TestSlaveCloud(this, 100));
        metaNectar.clouds.add(pc);

        ProvisionListener pl = new ProvisionListener(2 * masters);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("key", "value");
        for (int i = 0; i < masters; i++) {
            MasterServer ms = metaNectar.createMasterServer("org" + i);
            metaNectar.masterProvisioner.provision(ms, new URL("http://test/"), properties);
        }

        pl.await(1, TimeUnit.MINUTES);

        assertEquals(nodes, cl.online.size());
        assertEquals(nodes, metaNectar.masterProvisioner.masterLabel.getNodes().size());
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

        TerminateListener tl = new TerminateListener(2 * masters);

        for (int i = 0; i < masters; i++) {
            metaNectar.masterProvisioner.terminate(metaNectar.getMasterByOrganization("org" + i), true);
        }

        tl.await(1, TimeUnit.MINUTES);

        // TODO when node terminate is implemented need to assert that there are no nodes
//        assertEquals(nodes, MyComputerListener.get().online.size());
//        assertEquals(nodes, metaNectar.masterProvisioner.masterLabel.getNodes().size());

        assertEquals(0, MasterProvisioner.getProvisionedMasters(metaNectar).size());
    }


    public void testProvisionOneMasterOnMetaNectarNode() throws Exception {
        _testProvisionOnMetaNectarNode(1, 4);
    }

    private void _testProvisionOnMetaNectarNode(int masters, int nodesPerMaster) throws Exception {
        int nodes = masters / nodesPerMaster + Math.min(masters % nodesPerMaster, 1);

        Service s = new Service(100);
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(nodesPerMaster, s));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        ProvisionListener pl = new ProvisionListener(2 * masters);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("key", "value");
        for (int i = 0; i < masters; i++) {
            MasterServer ms = metaNectar.createMasterServer("org" + i);
            metaNectar.masterProvisioner.provision(ms, new URL("http://test/"), properties);
        }

        pl.await(1, TimeUnit.MINUTES);

        assertEquals(nodes, metaNectar.masterProvisioner.masterLabel.getNodes().size());
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

        TerminateListener tl = new TerminateListener(2 * masters);

        for (int i = 0; i < masters; i++) {
            metaNectar.masterProvisioner.terminate(metaNectar.getMasterByOrganization("org" + i), true);
        }

        tl.await(1, TimeUnit.MINUTES);

        assertEquals(0, MasterProvisioner.getProvisionedMasters(metaNectar).size());
    }


    public void testOrdinal1() throws Exception {
        config();

        MasterServer ms0 = provisionedMaster("0");
        assertEquals(0, ms0.getId());

        terminateMaster(ms0);

        ms0 = provisionedMaster("0");
        assertEquals(0, ms0.getId());
    }

    public void testOrdinal2() throws Exception {
        config();

        MasterServer ms0 = provisionedMaster("0");
        assertEquals(0, ms0.getId());

        MasterServer ms1 = provisionedMaster("1");
        assertEquals(1, ms1.getId());

        terminateMaster(ms0);

        ms0 = provisionedMaster("0");
        assertEquals(0, ms0.getId());

        terminateMaster(ms1);

        ms1 = provisionedMaster("1");
        assertEquals(1, ms1.getId());
    }


    public void testOrdinal4() throws Exception {
        config();

        MasterServer ms0 = provisionedMaster("0");
        assertEquals(0, ms0.getId());

        MasterServer ms1 = provisionedMaster("1");
        assertEquals(1, ms1.getId());

        MasterServer ms2 = provisionedMaster("2");
        assertEquals(2, ms2.getId());

        MasterServer ms3 = provisionedMaster("3");
        assertEquals(3, ms3.getId());

        terminateMaster(ms1);
        terminateMaster(ms2);

        ms1 = provisionedMaster("1");
        assertEquals(1, ms1.getId());

        ms2 = provisionedMaster("2");
        assertEquals(2, ms2.getId());

        terminateMaster(ms0);
        terminateMaster(ms1);
        terminateMaster(ms2);

        ms0 = provisionedMaster("0");
        assertEquals(0, ms0.getId());

        ms1 = provisionedMaster("1");
        assertEquals(1, ms1.getId());

        ms2 = provisionedMaster("2");
        assertEquals(2, ms2.getId());

    }

    private void config() throws Exception {
        Service s = new Service(100);
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(10, s));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());
    }

    private MasterServer provisionedMaster(String name) throws Exception {
        ProvisionListener pl = new ProvisionListener(2);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("key", "value");
        MasterServer ms = metaNectar.createMasterServer(name);
        metaNectar.masterProvisioner.provision(ms, new URL("http://test/"), properties);

        pl.await(1, TimeUnit.MINUTES);
        return ms;
    }

    private void terminateMaster(MasterServer ms) throws Exception {
        TerminateListener tl = new TerminateListener(2);

        metaNectar.masterProvisioner.terminate(ms, true);
        metaNectar.getItems().remove(ms);

        tl.await(1, TimeUnit.MINUTES);
        ms.delete();
    }
}
