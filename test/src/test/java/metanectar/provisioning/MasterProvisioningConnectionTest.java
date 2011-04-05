package metanectar.provisioning;

import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.*;
import hudson.model.listeners.ItemListener;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import hudson.tasks.Mailer;
import metanectar.model.JenkinsServer;
import metanectar.model.MetaNectar;
import metanectar.test.MetaNectarTestCase;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
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

    @Override
    protected void setUp() throws Exception {
        original = LoadStatistics.CLOCK;
        LoadStatistics.CLOCK = 10; // run x1000 the regular speed to speed up the test
        MasterProvisioner.MasterProvisionerInvoker.INITIALDELAY = 100;
        MasterProvisioner.MasterProvisionerInvoker.RECURRENCEPERIOD = 10;
        super.setUp();

        Mailer.descriptor().setHudsonUrl(getURL().toExternalForm());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        LoadStatistics.CLOCK = original;
        MasterProvisioner.MasterProvisionerInvoker.INITIALDELAY = original*10;
        MasterProvisioner.MasterProvisionerInvoker.RECURRENCEPERIOD = original;
    }

    public static class Listener implements MasterProvisioner.MasterProvisionListener {
        CountDownLatch cdl;

        Listener(CountDownLatch cdl) {
            this.cdl = cdl;
        }

        public void onProvisionStarted(String organization, Node n) {
            cdl.countDown();
        }

        public void onProvisionStartedError(String organization, Node n, Throwable error) {
        }

        public void onProvisionCompleted(Master m, Node n) {
            cdl.countDown();
        }

        public void onProvisionCompletedError(String organization, Node n, Throwable error) {
        }
    }

    public class Service implements MasterProvisioningService {

        private final int delay;

        Service(int delay) {
            this.delay = delay;
        }

        public Future<Master> provision(final VirtualChannel channel, final String organization, final URL metaNectarEndpoint, final Map<String, String> properties) throws IOException, InterruptedException {
            return Computer.threadPoolForRemoting.submit(new Callable<Master>() {
                public Master call() throws Exception {
                    System.out.println("Launching master " + organization);

                    Thread.sleep(delay);

                    final URL endpoint = channel.call(new TestMasterServerCallable(metaNectarEndpoint, organization, properties));

                    System.out.println("Launched master " + organization + ": " + endpoint);
                    return new Master(organization, endpoint);
                }
            });
        }

        public Future<?> terminate(VirtualChannel channel, String organization, boolean clean) throws IOException, InterruptedException {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Map<String, Master> getProvisioned(VirtualChannel channel) {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }

    public static class TestMasterServerCallable implements hudson.remoting.Callable<URL, Exception> {
        private final String organization;
        private final URL metaNectarEndpoint;
        private final Map<String, String> properties;

        public TestMasterServerCallable(URL metaNectarEndpoint, String organization, Map<String, String> properties) {
            this.organization = organization;
            this.metaNectarEndpoint = metaNectarEndpoint;
            this.properties = properties;
        }

        public URL call() throws Exception {
            TestMasterServer masterServer = new TestMasterServer(metaNectarEndpoint, organization, properties);
            masterServer.setRetryInterval(500);
            return masterServer.start();
        }
    }

    private class TestAgentProtocolListener extends MetaNectarAgentProtocol.Listener {

        private final MetaNectarAgentProtocol.Listener l;

        private final CountDownLatch onConnectedLatch;

        private final CountDownLatch onRefusalLatch;

        public TestAgentProtocolListener(MetaNectarAgentProtocol.Listener l, CountDownLatch onConnectedLatch, CountDownLatch onRefusalLatch) {
            this.l = l;
            this.onConnectedLatch = onConnectedLatch;
            this.onRefusalLatch = onRefusalLatch;
        }

        public URL getEndpoint() throws IOException {
            return l.getEndpoint();
        }

        public void onConnectingTo(URL address, X509Certificate identity, String organization, Map<String, String> properties) throws GeneralSecurityException, IOException {
            l.onConnectingTo(address, identity, organization, properties);
        }

        public void onConnectedTo(Channel channel, X509Certificate identity, String organization) throws IOException {
            l.onConnectedTo(channel, identity, organization);
            if (onConnectedLatch != null)
                onConnectedLatch.countDown();
        }

        @Override
        public void onRefusal(MetaNectarAgentProtocol.GracefulConnectionRefusalException e) throws Exception {
            if (onRefusalLatch != null)
                onRefusalLatch.countDown();

            l.onRefusal(e);
        }

        @Override
        public void onError(Exception e) throws Exception {
            l.onError(e);
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
        HtmlPage wc = new WebClient().goTo("/");

        CountDownLatch onEventCdl = new CountDownLatch(masters);
        metaNectar.configureNectarAgentListener(new TestAgentProtocolListener(new MetaNectar.AgentProtocolListener(metaNectar), onEventCdl, onEventCdl));

        TestSlaveCloud cloud = new TestSlaveCloud(this, 100);
        metaNectar.clouds.add(cloud);

        CountDownLatch masterProvisionCdl = new CountDownLatch(2 * masters);
        Listener l = new Listener(masterProvisionCdl);
        Service s = new Service(100);

        for (int i = 0; i < masters; i++) {
            metaNectar.provisionMaster(l, s, "org" + i);
        }

        // Wait for masters to be provisioned
        masterProvisionCdl.await(1, TimeUnit.MINUTES);

        // Wait for masters to be connected
        onEventCdl.await(1, TimeUnit.MINUTES);

        assertEquals(masters, metaNectar.getItems(JenkinsServer.class).size());
        for (JenkinsServer js : metaNectar.getItems(JenkinsServer.class)) {
            assertTrue(js.isApproved());
        }
    }

    private class TestApprovingAgentProtocolListener extends TestAgentProtocolListener {

        public TestApprovingAgentProtocolListener(MetaNectarAgentProtocol.Listener l, CountDownLatch onConnectedLatch, CountDownLatch onRefusalLatch) {
            super(l, onConnectedLatch, onRefusalLatch);
        }

        public void onConnectingTo(URL address, X509Certificate identity, String organization, Map<String, String> properties) throws GeneralSecurityException, IOException {
            try {
                super.onConnectingTo(address, identity, organization, properties);
            } catch (MetaNectarAgentProtocol.GracefulConnectionRefusalException e) {
                JenkinsServer server = metaNectar.getServerByIdentity(identity.getPublicKey());
                server.setApproved(true);
                throw e;
            }
        }
    }

    public void testProvisionOneMasterNoGrant() throws Exception {
        _testProvisionNoGrant(1);
    }

    public void testProvisionTwoMasterNoGrant() throws Exception {
        _testProvisionNoGrant(2);
    }

    public void testProvisionFourMasterNoGrant() throws Exception {
        _testProvisionNoGrant(4);
    }

    public void testProvisionEightMasterNoGrant() throws Exception {
        _testProvisionNoGrant(8);
    }

    public void _testProvisionNoGrant(int masters) throws Exception {
        HtmlPage wc = new WebClient().goTo("/");

        CountDownLatch onEventCdl = new CountDownLatch(2 * masters);
        metaNectar.configureNectarAgentListener(new TestApprovingAgentProtocolListener(new MetaNectar.AgentProtocolListener(metaNectar), onEventCdl, onEventCdl));

        TestSlaveCloud cloud = new TestSlaveCloud(this, 100);
        metaNectar.clouds.add(cloud);

        CountDownLatch masterProvisionCdl = new CountDownLatch(2 * masters);
        Listener l = new Listener(masterProvisionCdl);
        Service s = new Service(100);

        for (int i = 0; i < masters; i++) {
            metaNectar.provisionMaster(l, s, "org" + i, false);
        }

        // Wait for masters to be provisioned
        masterProvisionCdl.await(1, TimeUnit.MINUTES);

        // Wait for masters to be approved and connected
        onEventCdl.await(1, TimeUnit.MINUTES);

        assertEquals(masters, metaNectar.getItems(JenkinsServer.class).size());
        for (JenkinsServer js : metaNectar.getItems(JenkinsServer.class)) {
            assertTrue(js.isApproved());
        }
    }

}
