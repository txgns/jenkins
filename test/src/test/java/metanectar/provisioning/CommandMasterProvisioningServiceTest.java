package metanectar.provisioning;

import com.google.common.collect.Maps;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.slaves.DumbSlave;
import metanectar.model.MasterServer;
import metanectar.model.MasterServerListener;
import metanectar.model.MetaNectar;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TODO, these tests most likely only work on unix unless the rm command is available on windows.
 *
 * @author Paul Sandoz
 */
public class CommandMasterProvisioningServiceTest extends AbstractMasterProvisioningTest {

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

    public void testProvisionLocally() throws Exception {
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(1, getDefaultCommand()));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        terminate(provision());
    }

    public void testProvisionRemotely() throws Exception {
        DumbSlave slave = createSlave(metaNectar.masterProvisioner.masterLabel);
        slave.getNodeProperties().add(new MasterProvisioningNodeProperty(1, getDefaultCommand()));
        Computer computer = slave.toComputer();
        computer.connect(false).get();

        terminate(provision());
    }

    private File terminatingFile;

    private File getTerminatingFile() throws IOException {
        if (terminatingFile == null)
            terminatingFile = File.createTempFile("pre-", "-suf");

        return terminatingFile;
    }

    private File homeDir;

    private File getHomeDir() throws IOException {
        if (homeDir == null) {
            homeDir = new File(getTerminatingFile().getParent(), UUID.randomUUID().toString());
            homeDir.deleteOnExit();
        }

        return homeDir;
    }

    private CommandMasterProvisioningService getDefaultCommand() throws IOException {
        return getCommand(
                "echo \"" + getTestUrl() + "\"",
                "rm " + getTerminatingFile().toString());
    }

    private CommandMasterProvisioningService getCommand(String provisionCommand, String terminateCommand) throws IOException {
        return new CommandMasterProvisioningService(8080, getHomeDir().toString(), 2,
                provisionCommand,
                terminateCommand);
    }

    private MasterServer provision() throws Exception {
        CountDownLatch cdl = new CountDownLatch(2);
        ProvisionListener.get().init(cdl);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(MetaNectar.GRANT_PROPERTY, "grant");
        MasterServer ms = metaNectar.createMasterServer("org1");
        metaNectar.masterProvisioner.provision(ms, new URL("http://test/"), properties);

        cdl.await(1, TimeUnit.MINUTES);

        Map<String, String> params = getParams(ms.getEndpoint());

        String port = params.get(CommandMasterProvisioningService.Variable.MASTER_PORT.toString());
        assertNotNull(port);
        assertEquals(8080, Integer.valueOf(port) + ms.getId());

        File homeFile = new File(getHomeDir(), "org1");
        String home = params.get(CommandMasterProvisioningService.Variable.MASTER_HOME.toString());
        assertNotNull(home);
        assertEquals(home, homeFile.toString());

        String metaNectarEndpoint = params.get(CommandMasterProvisioningService.Variable.MASTER_METANECTAR_ENDPOINT.toString());
        assertNotNull(metaNectarEndpoint);
        assertEquals(metaNectarEndpoint, new URL("http://test/").toExternalForm());

        String grantId = params.get(CommandMasterProvisioningService.Variable.MASTER_GRANT_ID.toString());
        assertNotNull(grantId);
        assertEquals(grantId, "grant");

        assertTrue(homeFile.exists());
        return ms;
    }

    private void terminate(MasterServer ms) throws Exception {
        assertTrue(getTerminatingFile().exists());

        CountDownLatch cdl = new CountDownLatch(2);
        TerminateListener.get().init(cdl);

        metaNectar.masterProvisioner.terminate(ms, false);

        cdl.await(1, TimeUnit.MINUTES);

        assertFalse(getTerminatingFile().exists());
    }

    private String getTestUrl() {
        boolean first = true;
        StringBuilder sb = new StringBuilder("http://test?");
        for (CommandMasterProvisioningService.Variable v : CommandMasterProvisioningService.Variable.values()) {
            if (!first)
                sb.append("&");
            first = false;
            sb.append(v.toString()).append("=").append("${").append(v.toString()).append("}");
        }

        return sb.toString();
    }

    private Map<String, String> getParams(URL u) {
        Map<String, String> params = Maps.newHashMap();
        for (String param : u.getQuery().split("&")) {
            String[] nameValue = param.split("=");
            params.put(nameValue[0], nameValue[1]);
        }

        return params;
    }
}
