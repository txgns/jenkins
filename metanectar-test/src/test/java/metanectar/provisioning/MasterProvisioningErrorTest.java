package metanectar.provisioning;

import hudson.model.Computer;
import metanectar.LatchConnectedMasterListener;
import metanectar.model.MasterServer;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Paul Sandoz
 */
public class MasterProvisioningErrorTest extends AbstractMasterProvisioningTestCase {

    public static class ErrorProvisioningService extends MasterProvisioningService {

        private final int delay;

        ErrorProvisioningService(int delay) {
            this.delay = delay;
        }

        public Future<Provisioned> provision(final MasterServer ms, final URL metaNectarEndpoint, Map<String, Object> properties) throws IOException, InterruptedException {
            return Computer.threadPoolForRemoting.submit(new Callable<Provisioned>() {
                public Provisioned call() throws Exception {
                    Thread.sleep(delay);

                    throw new Exception();
                }
            });
        }

        public Future<?> start(final MasterServer ms) throws Exception {
            return getFuture("starting master");
        }

        public Future<?> stop(final MasterServer ms) throws Exception {
            return getFuture("stopping master");
        }

        public Future<Terminated> terminate(final MasterServer ms) throws IOException, InterruptedException {
            return Computer.threadPoolForRemoting.submit(new Callable<Terminated>() {
                public Terminated call() throws Exception {
                    Thread.sleep(delay);

                    throw new Exception();
                }
            });
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

    public void testProvisionWithError() throws Exception {
        new WebClient().goTo("/");

        MasterServer ms = metaNectar.createManagedMaster("org");

        LatchMasterServerListener provisioningError = new LatchMasterServerListener(1) {
            public void onProvisioningError(MasterServer ms) {
                countDown();
            }
        };

        // Add provisioning resources
        ErrorProvisioningService s = new ErrorProvisioningService(100);
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(4, s));

        ms.provisionAndStartAction();
        provisioningError.await(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.ProvisioningError, ms.getState());
        assertEquals(Exception.class, ms.getError().getClass());
        assertEquals(1, metaNectar.masterProvisioner.getProvisionedMasters().size());
    }


    public static class ErrorTerminatingService extends metanectar.provisioning.DummyMasterProvisioningService {

         ErrorTerminatingService(int delay) {
             super(delay);
         }


         public Future<Terminated> terminate(final MasterServer ms) throws IOException, InterruptedException {
             return Computer.threadPoolForRemoting.submit(new Callable<Terminated>() {
                 public Terminated call() throws Exception {
                     Thread.sleep(getDelay());

                     throw new Exception();
                 }
             });
         }
    }

    public void testTerminateWithError() throws Exception {
        new WebClient().goTo("/");

        MasterServer ms = metaNectar.createManagedMaster("org");

        LatchMasterServerListener approved = new LatchMasterServerListener.ProvisionListener(3) {
            public void onApproved(MasterServer ms) {
                countDown();
            }
        };

        LatchConnectedMasterListener connected = new LatchConnectedMasterListener.ConnectedListener(1);

        // Add provisioning resources
        ErrorTerminatingService s = new ErrorTerminatingService(100);
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(4, s));

        ms.provisionAndStartAction();
        approved.await(1, TimeUnit.MINUTES);
        connected.await(1, TimeUnit.MINUTES);

        LatchMasterServerListener provisioningError = new LatchMasterServerListener(1) {
            public void onTerminatingError(MasterServer ms) {
                countDown();
            }
        };

        ms.stopAndTerminateAction();
        provisioningError.await(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.TerminatingError, ms.getState());
        assertEquals(Exception.class, ms.getError().getClass());
        assertEquals(1, metaNectar.masterProvisioner.getProvisionedMasters().size());
    }
}
