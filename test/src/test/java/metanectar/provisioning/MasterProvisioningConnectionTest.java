package metanectar.provisioning;

import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Extension;
import hudson.model.*;
import hudson.remoting.Channel;
import hudson.tasks.Mailer;
import metanectar.model.MasterServer;
import metanectar.model.MasterServerListener;
import metanectar.model.MetaNectar;
import metanectar.test.MetaNectarTestCase;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
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

    @Extension
    public static class Listener extends MasterServerListener {
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

        public static Listener get() {
            return Hudson.getInstance().getExtensionList(MasterServerListener.class).get(Listener.class);
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

        TestMasterProvisioningService s = new TestMasterProvisioningService(100);
        TestSlaveCloud cloud = new TestSlaveCloud(this, 4, s, 100);
        metaNectar.clouds.add(cloud);

        CountDownLatch masterProvisionCdl = new CountDownLatch(2 * masters);
        Listener.get().init(masterProvisionCdl);

        for (int i = 0; i < masters; i++) {
            MasterServer ms = metaNectar.createMasterServer("org" + i);
            metaNectar.provisionMaster(ms);
        }

        // Wait for masters to be provisioned
        masterProvisionCdl.await(1, TimeUnit.MINUTES);

        // Wait for masters to be connected
        onEventCdl.await(1, TimeUnit.MINUTES);

        assertEquals(masters, metaNectar.getItems(MasterServer.class).size());
        for (MasterServer js : metaNectar.getItems(MasterServer.class)) {
            assertTrue(js.isApproved());
        }
    }
}
