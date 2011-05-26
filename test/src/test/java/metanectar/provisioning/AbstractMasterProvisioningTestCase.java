package metanectar.provisioning;

import com.google.common.collect.Lists;
import hudson.model.Computer;
import hudson.model.LoadStatistics;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import metanectar.model.MasterServer;
import metanectar.test.MetaNectarTestCase;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Paul Sandoz
 */
public abstract class AbstractMasterProvisioningTestCase extends MetaNectarTestCase {
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

    public static class DummyMasterProvisioningService extends MasterProvisioningService {

        private final int delay;

        DummyMasterProvisioningService(int delay) {
            this.delay = delay;
        }

        public Future<Master> provision(VirtualChannel channel, TaskListener listener,
                                        int id, final String organization, final URL metaNectarEndpoint, Map<String, Object> properties) throws IOException, InterruptedException {
            return Computer.threadPoolForRemoting.submit(new Callable<Master>() {
                public Master call() throws Exception {
                    Thread.sleep(delay);

                    System.out.println("provisioning master");

                    return new Master(organization, metaNectarEndpoint);
                }
            });
        }

        public Future<?> start(VirtualChannel channel, TaskListener listener,
                                        String name) throws Exception {
            return getFuture("starting master");
        }

        public Future<?> stop(VirtualChannel channel, TaskListener listener,
                                        String name) throws Exception {
            return getFuture("stopping master");
        }

        public Future<?> terminate(VirtualChannel channel, TaskListener listener,
                                   String organization, boolean clean) throws IOException, InterruptedException {
            return getFuture("terminating master");
        }

        private Future<?> getFuture(final String s) {
            return Computer.threadPoolForRemoting.submit(new Callable<Void>() {
                public Void call() throws Exception {
                    Thread.sleep(delay);

                    System.out.println(s);

                    return null;
                }
            });
        }

    }

    public void configureDummyMasterProvisioningOnMetaNectar() throws Exception {
        configureDummyMasterProvisioningOnMetaNectar(10);
    }

    public void configureDummyMasterProvisioningOnMetaNectar(int n) throws Exception {
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(n, new DummyMasterProvisioningService(100)));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());
    }

    public MasterServer provisionAndStartMaster(String name) throws Exception {
        LatchMasterServerListener.ProvisionAndStartListener pl = new LatchMasterServerListener.ProvisionAndStartListener(4);

        MasterServer ms = metaNectar.createMasterServer(name);
        ms.provisionAndStartAction();

        pl.await(1, TimeUnit.MINUTES);
        return ms;
    }

    public List<MasterServer> provisionAndStartMasters(String name, int n) throws Exception {
        LatchMasterServerListener.ProvisionAndStartListener pl = new LatchMasterServerListener.ProvisionAndStartListener(n * 4);

        List<MasterServer> l = Lists.newArrayList();
        for (int i = 0; i < n ; i++) {
            MasterServer ms = metaNectar.createMasterServer(name + i);
            ms.provisionAndStartAction();
            l.add(ms);
        }

        pl.await(1, TimeUnit.MINUTES);
        return l;
    }

    public void terminateAndDeleteMaster(MasterServer ms) throws Exception {
        LatchMasterServerListener.StopAndTerminateListener tl = new LatchMasterServerListener.StopAndTerminateListener(4);

        ms.stopAndTerminateAction(true);
        metaNectar.getItems().remove(ms);

        tl.await(1, TimeUnit.MINUTES);
        ms.delete();
    }

    public void terminateAndDeleteMasters(List<MasterServer> l) throws Exception {
        LatchMasterServerListener.StopAndTerminateListener tl = new LatchMasterServerListener.StopAndTerminateListener(l.size() * 4);

        for (MasterServer ms : l) {
            ms.stopAndTerminateAction(true);
        }

        tl.await(1, TimeUnit.MINUTES);

        for (MasterServer ms : l) {
            metaNectar.getItems().remove(ms);
            ms.delete();
        }
    }
}
