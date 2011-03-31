package metanectar.provisioning;

import com.google.common.collect.Maps;
import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.ItemListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import hudson.tasks.Mailer;
import metanectar.model.JenkinsServer;
import metanectar.model.MetaNectar;
import metanectar.test.MetaNectarTestCase;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

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
public class MasterProvisioningConnectionTest extends MetaNectarTestCase {
    private int original;

    //private InstanceIdentity id;

    @Override
    protected void setUp() throws Exception {
        original = LoadStatistics.CLOCK;
        LoadStatistics.CLOCK = 10; // run x1000 the regular speed to speed up the test
        MasterProvisioner.MasterProvisionerInvoker.INITIALDELAY = 100;
        MasterProvisioner.MasterProvisionerInvoker.RECURRENCEPERIOD = 10;
        super.setUp();

        Mailer.descriptor().setHudsonUrl(getURL().toExternalForm());
//        id = InstanceIdentity.get();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        LoadStatistics.CLOCK = original;
        MasterProvisioner.MasterProvisionerInvoker.INITIALDELAY = original*10;
        MasterProvisioner.MasterProvisionerInvoker.RECURRENCEPERIOD = original;
    }

    public static class Listener implements MasterProvisioner.MasterListener {
        CountDownLatch cdl;

        Listener(CountDownLatch cdl) {
            this.cdl = cdl;
        }

        public void onProvisioningMaster(String organization, Node n) {
            cdl.countDown();
        }

        public void onErrorProvisioningMaster(String organization, Node n, Throwable error) {
        }

        public void onProvisionedMaster(Master m, Node n) {
            cdl.countDown();
        }

        public void onErrorProvisionedMaster(String organization, Node n, Throwable error) {
        }

        public void onUnprovisionedMaster(Master m, Node n) {
        }
    }

    public class Service implements MasterProvisioningService {

        private final int delay;

        Service(int delay) {
            this.delay = delay;
        }

        public Future<Master> provision(final VirtualChannel channel, final String organization, final URL metaNectarEndpoint, Map<String, String> properties) throws IOException, InterruptedException {
            return Computer.threadPoolForRemoting.submit(new Callable<Master>() {
                public Master call() throws Exception {
                    Thread.sleep(delay);

                    System.out.println("launching master");

                    // TODO in process or external process
                    final URL endpoint = channel.call(new TestMasterServerCallable(metaNectarEndpoint, organization));
//                    final URL endpoint = new TestMasterServerCallable(metaNectarEndpoint, organization).call();

                    System.out.println("Master: " + endpoint);
                    return new Master(organization, endpoint);
                }
            });
        }

        public Future<?> delete(VirtualChannel channel, String organization, boolean clean) throws IOException, InterruptedException {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Map<String, Master> getProvisioned(VirtualChannel channel) {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }

    public static class TestMasterServerCallable implements hudson.remoting.Callable<URL, Exception> {
        private final String organization;
        private final URL metaNectarEndpoint;

        public TestMasterServerCallable(URL metaNectarEndpoint, String organization) {
            this.organization = organization;
            this.metaNectarEndpoint = metaNectarEndpoint;
        }

        public URL call() throws Exception {
            TestMasterServer masterServer = new TestMasterServer(metaNectarEndpoint, organization);
            masterServer.setRetryInterval(500);
            return masterServer.start();
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
        TestSlaveCloud cloud = new TestSlaveCloud(this, 100);
        metaNectar.clouds.add(cloud);

        CountDownLatch cdl = new CountDownLatch(2 * masters);
        Listener l = new Listener(cdl);
        Service s = new Service(100);

        Map<String, String> properties = new HashMap<String, String>();
        properties.put("key", "value");
        for (int i = 0; i < masters; i++) {
            metaNectar.masterProvisioner.provision(l, s, "org" + i, new URL(metaNectar.getRootUrl()), properties);
        }

        // Wait for master to be provisioned
        cdl.await(1, TimeUnit.MINUTES);

        Thread.sleep(1000);
        int retry = 0;
        Set<JenkinsServer> connected = new HashSet<JenkinsServer>();
        while (true) {
            List<JenkinsServer> items = metaNectar.getItems(JenkinsServer.class);

            for (JenkinsServer js : items) {
                if (!js.isApproved()) {
                    assertNull(js.getChannel());
                    js.setApproved(true);
                } else if (js.getChannel() != null) {
                    connected.add(js);
                }
            }

            if (connected.size() == masters)
                break;

            if (retry > 1000) {
                fail(connected.size() + " out of " + masters + " connected");
            }

            retry++;
            Thread.sleep(10);
        }
    }

}
