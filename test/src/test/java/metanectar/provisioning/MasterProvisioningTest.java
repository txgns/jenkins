package metanectar.provisioning;

import hudson.Extension;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.slaves.*;
import metanectar.model.MasterServer;
import metanectar.model.MasterServerListener;
import metanectar.model.MetaNectar;
import metanectar.test.MetaNectarTestCase;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Paul Sandoz
 */
public class MasterProvisioningTest extends MetaNectarTestCase {
    private int original;

    @Override
    protected void setUp() throws Exception {
        original = LoadStatistics.CLOCK;
        LoadStatistics.CLOCK = 10; // run x1000 the regular speed to speed up the test
        MasterProvisioner.MasterProvisionerInvoker.INITIALDELAY = 100;
        MasterProvisioner.MasterProvisionerInvoker.RECURRENCEPERIOD = 10;
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        LoadStatistics.CLOCK = original;
        MasterProvisioner.MasterProvisionerInvoker.INITIALDELAY = original*10;
        MasterProvisioner.MasterProvisionerInvoker.RECURRENCEPERIOD = original;
    }

    @Extension
    public static class ProvisionListener extends MasterServerListener {
        CountDownLatch cdl;

        void init(CountDownLatch cdl) {
            this.cdl = cdl;
        }

        public void onProvisioning(MasterServer ms) {
            if (cdl != null)
                cdl.countDown();
        }

        public void onProvisioned(MasterServer ms) {
            if (cdl != null)
                cdl.countDown();
        }

        public static ProvisionListener get() {
            return Hudson.getInstance().getExtensionList(MasterServerListener.class).get(ProvisionListener.class);
        }
    }

    @Extension
    public static class TerminateListener extends MasterServerListener {
        CountDownLatch cdl;

        void init(CountDownLatch cdl) {
            this.cdl = cdl;
        }

        public void onTerminating(MasterServer ms) {
            if (cdl != null)
                cdl.countDown();
        }

        public void onTerminated(MasterServer ms) {
            if (cdl != null)
                cdl.countDown();
        }

        public static TerminateListener get() {
            return Hudson.getInstance().getExtensionList(MasterServerListener.class).get(TerminateListener.class);
        }
    }

    public static class Service extends MasterProvisioningService {

        private final int delay;

        Service(int delay) {
            this.delay = delay;
        }

        public Future<Master> provision(VirtualChannel channel, final String organization, final URL metaNectarEndpoint, Map<String, Object> properties) throws IOException, InterruptedException {
            return Computer.threadPoolForRemoting.submit(new Callable<Master>() {
                public Master call() throws Exception {
                    Thread.sleep(delay);

                    System.out.println("launching master");

                    return new Master(organization, metaNectarEndpoint);
                }
            });
        }

        public Future<?> terminate(VirtualChannel channel, String organization, boolean clean) throws IOException, InterruptedException {
            return Computer.threadPoolForRemoting.submit(new Callable<Void>() {
                public Void call() throws Exception {
                    Thread.sleep(delay);

                    System.out.println("deleting master");

                    return null;
                }
            });
        }

        public Map<String, Master> getProvisioned(VirtualChannel channel) {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }

    @Extension
    public static class MyComputerListener extends ComputerListener {

        Set<Node> online = new HashSet<Node>();

        public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
            System.out.println("ONLINE: " + c.getNode().getDisplayName());
            if (MetaNectar.getInstance().masterProvisioner != null && MetaNectar.getInstance().masterProvisioner.masterLabel.matches(c.getNode()))
                online.add(c.getNode());
        }

        public void onOffline(Computer c) {
            System.out.println("OFFLINE: " + c.getNode().getDisplayName());
            if (MetaNectar.getInstance().masterProvisioner.masterLabel.matches(c.getNode()))
                online.remove(c.getNode());
        }

        static MyComputerListener get() {
            return ComputerListener.all().get(MyComputerListener.class);
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

        Service s = new Service(100);
        TestSlaveCloud cloud = new TestSlaveCloud(this, nodesPerMaster, s, 100);
        metaNectar.clouds.add(cloud);

        CountDownLatch cdl = new CountDownLatch(2 * masters);
        ProvisionListener.get().init(cdl);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("key", "value");
        for (int i = 0; i < masters; i++) {
            MasterServer ms = metaNectar.createMasterServer("org" + i);
            metaNectar.masterProvisioner.provision(ms, new URL("http://test/"), properties);
        }

        cdl.await(1, TimeUnit.MINUTES);

        assertEquals(nodes, MyComputerListener.get().online.size());
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

        CountDownLatch cdl = new CountDownLatch(2 * masters);
        TerminateListener.get().init(cdl);

        for (int i = 0; i < masters; i++) {
            metaNectar.masterProvisioner.terminate(metaNectar.getMasterByOrganization("org" + i), true);
        }

        cdl.await(1, TimeUnit.MINUTES);

        // TODO when node terminate is implemented need to assert that there are no nodes
//        assertEquals(nodes, MyComputerListener.get().online.size());
//        assertEquals(nodes, metaNectar.masterProvisioner.masterLabel.getNodes().size());

        assertEquals(0, MasterProvisioner.getProvisionedMasters(metaNectar).size());
    }



}
