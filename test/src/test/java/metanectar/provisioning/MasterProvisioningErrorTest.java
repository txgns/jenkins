package metanectar.provisioning;

import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
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

        public Future<Master> provision(VirtualChannel channel, TaskListener listener,
                                        int id, final String organization, final URL metaNectarEndpoint, Map<String, Object> properties) throws IOException, InterruptedException {
            return Computer.threadPoolForRemoting.submit(new Callable<Master>() {
                public Master call() throws Exception {
                    Thread.sleep(delay);

                    throw new Exception();
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
            return Computer.threadPoolForRemoting.submit(new Callable<Void>() {
                public Void call() throws Exception {
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

        MasterServer ms = metaNectar.createMasterServer("org");

        LatchMasterServerListener provisioningError = new LatchMasterServerListener(1) {
            public void onProvisioningError(MasterServer ms) {
                countDown();
            }
        };

        // Add provisioning resources
        ErrorProvisioningService s = new ErrorProvisioningService(100);
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(4, s));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        ms.provisionAndStartAction();
        provisioningError.await(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.ProvisioningError, ms.getState());
        assertEquals(Exception.class, ms.getError().getClass());
    }


    public static class ErrorTerminatingService extends TestMasterProvisioningService {

         ErrorTerminatingService(int delay) {
             super(delay);
         }

         public Future<?> terminate(VirtualChannel channel, TaskListener listener,
                                    String organization, boolean clean) throws IOException, InterruptedException {
             return Computer.threadPoolForRemoting.submit(new Callable<Void>() {
                 public Void call() throws Exception {
                     Thread.sleep(getDelay());

                     throw new Exception();
                 }
             });
         }
    }

    public void testTerminateWithError() throws Exception {
        new WebClient().goTo("/");

        MasterServer ms = metaNectar.createMasterServer("org");

        LatchMasterServerListener connected = new LatchMasterServerListener.ProvisionListener(4) {
            public void onApproved(MasterServer ms) {
                countDown();
            }

            public void onConnected(MasterServer ms) {
                countDown();
            }
        };

        // Add provisioning resources
        ErrorTerminatingService s = new ErrorTerminatingService(100);
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(4, s));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        ms.provisionAndStartAction();
        connected.await(1, TimeUnit.MINUTES);

        LatchMasterServerListener provisioningError = new LatchMasterServerListener(1) {
            public void onTerminatingError(MasterServer ms) {
                countDown();
            }
        };

        ms.stopAndTerminateAction(false);
        provisioningError.await(1, TimeUnit.MINUTES);
        assertEquals(MasterServer.State.TerminatingError, ms.getState());
        assertEquals(Exception.class, ms.getError().getClass());
    }
}
