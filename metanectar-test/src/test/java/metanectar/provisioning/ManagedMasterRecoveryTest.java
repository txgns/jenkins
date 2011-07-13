package metanectar.provisioning;

import com.google.common.collect.Lists;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import metanectar.cloud.MasterProvisioningCloud;
import metanectar.cloud.MasterProvisioningCloudProxy;
import metanectar.model.MasterServer;

import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/**
 *
 * @author Paul Sandoz
 */
public class ManagedMasterRecoveryTest extends AbstractMasterProvisioningTestCase {

    public void testRecoverFromNoResources() throws Exception {
        MasterServer ms = metaNectar.createManagedMaster("m");

        LatchMasterServerListener l = new LatchMasterServerListener(1) {
            public void onProvisioningErrorNoResources(MasterServer ms) {
                countDown();
            }
        };

        ms.provisionAction();
        l.await(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.ProvisioningErrorNoResources, ms.getState());

        metaNectar.masterProvisioner.getPendingMasterRequests().clear();
        init(WaitingService.Wait.none);

        ms.initiateRecovery().get(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Provisioned, ms.getState());
    }

    public void testRecoverFromPreProvisioning() throws Exception {
        MasterProvisioningNodePropertyTemplate tp = new MasterProvisioningNodePropertyTemplate(1, new WaitingService(WaitingService.Wait.none));
        MasterProvisioningCloudProxy pc = new MasterProvisioningCloudProxy(tp, new WaitingCloud());
        metaNectar.clouds.add(pc);

        LatchMasterProvisioningCloudListener l = new LatchMasterProvisioningCloudListener.ProvisionListener(1);

        MasterServer ms = metaNectar.createManagedMaster("m");
        ms.provisionAction();
        l.await(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.PreProvisioning, ms.getState());

        metaNectar.masterProvisioner.getPendingMasterRequests().clear();
        metaNectar.masterProvisioner.getNodeTaskQueue().getQueue().clear();
        metaNectar.clouds.clear();

        reset();

        ms.initiateRecovery().get(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Provisioned, ms.getState());
    }

    public void testRecoverFromProvisioning() throws Exception {
        WaitingService ws = init(WaitingService.Wait.provision);

        MasterServer ms = metaNectar.createManagedMaster("m");
        ms.provisionAction();

        ws.waitingLatch.await(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Provisioning, ms.getState());

        reset();

        ms.initiateRecovery().get(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Provisioned, ms.getState());
    }

    public void testRecoverFromStarting() throws Exception {
        WaitingService ws = init(WaitingService.Wait.start);

        MasterServer ms = metaNectar.createManagedMaster("m");
        ms.provisionAndStartAction();

        ws.waitingLatch.await(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Starting, ms.getState());

        reset();

        ms.initiateRecovery().get(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Started, ms.getState());
    }

    public void testRecoverFromStopping() throws Exception {
        WaitingService ws = init(WaitingService.Wait.stop);

        MasterServer ms = metaNectar.createManagedMaster("m");
        ms.provisionAndStartAction().get(1, TimeUnit.MINUTES);

        ms.stopAction();
        ws.waitingLatch.await(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Stopping, ms.getState());

        reset();

        ms.initiateRecovery().get(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Stopped, ms.getState());
    }

    public void testRecoverFromTerminating() throws Exception {
        WaitingService ws = init(WaitingService.Wait.terminate);

        MasterServer ms = metaNectar.createManagedMaster("m");
        ms.provisionAndStartAction().get(1, TimeUnit.MINUTES);

        ms.stopAndTerminateAction();
        ws.waitingLatch.await(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Terminating, ms.getState());

        reset();

        ms.initiateRecovery().get(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Terminated, ms.getState());
    }

    private WaitingService init(WaitingService.Wait type) throws Exception {
        WaitingService ws = new WaitingService(type);
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(1, ws));
        return ws;
    }

    private void reset() throws Exception {
        // Forget about the waiting task
        metaNectar.masterProvisioner.getMasterServerTaskQueue().getQueue().clear();
        metaNectar.getNodeProperties().clear();
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(1, new WaitingService(WaitingService.Wait.none)));
    }

    private MasterServer terminate(MasterServer ms) throws Exception {
        ms.stopAndTerminateAction().get(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Terminated, ms.getState());

        return ms;
    }

    private MasterServer provision(MasterServer ms) throws Exception {
        ms.provisionAndStartAction().get(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.Started, ms.getState());

        return ms;
    }

    static class WaitingCloud extends Cloud implements MasterProvisioningCloud {
        WaitingCloud() {
            super("waiting-cloud");
        }

        @Override
        public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
            return Lists.newArrayList(new NodeProvisioner.PlannedNode("waiting-cloud-node", ManagedMasterRecoveryTest.<Node>createWaitingFuture(), 1));
        }

        @Override
        public boolean canProvision(Label label) {
            return true;
        }
    }

    static class WaitingService extends MasterProvisioningService {

        CountDownLatch waitingLatch = new CountDownLatch(1);

        enum Wait {
            none,
            provision,
            start,
            stop,
            terminate
        }

        Wait type;

        WaitingService(Wait type) {
            this.type = type;
        }

        @Override
        public Future<Provisioned> provision(MasterServer ms, URL metaNectarEndpoint, Map<String, Object> properties) throws Exception {
            if (type == Wait.provision) {
                waitingLatch.countDown();
                return createWaitingFuture();
            } else {
                return createValueFuture(new Provisioned("home", new URL("http://localhost:8080")));
            }
        }

        @Override
        public Future<?> start(MasterServer ms) throws Exception {
            if (type == Wait.start) {
                waitingLatch.countDown();
                return createWaitingFuture();
            } else {
                return createNoOpFuture();
            }
        }

        @Override
        public Future<?> stop(MasterServer ms) throws Exception {
            if (type == Wait.stop) {
                waitingLatch.countDown();
                return createWaitingFuture();
            } else {
                return createNoOpFuture();
            }
        }

        @Override
        public Future<Terminated> terminate(MasterServer ms) throws Exception {
            if (type == Wait.terminate) {
                waitingLatch.countDown();
                return createWaitingFuture();
            } else {
                return createValueFuture(new Terminated(new URL("file:/tmp/x.zip")));
            }
        }
    }

    private static Future<?> createNoOpFuture() {
        return createFuture(new Callable<Void>() {
            public Void call() throws Exception {
                return null;
            }
        });
    }

    private static <T> Future<T> createValueFuture(final T t) {
        return createFuture(new Callable<T>() {
            public T call() throws Exception {
                return t;
            }
        });
    }

    private static <T> Future<T> createWaitingFuture() {
        return createFuture(new Callable<T>() {
            public T call() throws Exception {
                while (true) {
                    try {
                        Thread.currentThread().sleep(TimeUnit.HOURS.toMillis(1));
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        });
    }

    private static <T> Future<T> createFuture(Callable<T> c) {
        return Computer.threadPoolForRemoting.submit(c);
    }

}
