package metanectar.provisioning;

import com.google.common.collect.Maps;
import hudson.model.Computer;
import hudson.slaves.DumbSlave;
import metanectar.Config;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static metanectar.provisioning.LatchMasterServerListener.ProvisionListener;
import static metanectar.provisioning.LatchMasterServerListener.TerminateListener;

/**
 * TODO, these tests most likely only work on unix unless the rm command is available on windows.
 *
 * @author Paul Sandoz
 */
public class CommandMasterProvisioningServiceTest extends AbstractMasterProvisioningTest {
    public void testConfigProvisionLocally() throws Exception {
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(1, getConfigCommand()));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        terminate(provision());
    }

    public void testConfigProvisionRemotely() throws Exception {
        DumbSlave slave = createSlave(metaNectar.masterProvisioner.masterLabel);
        slave.getNodeProperties().add(new MasterProvisioningNodeProperty(1, getConfigCommand()));
        Computer computer = slave.toComputer();
        computer.connect(false).get();

        terminate(provision());
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


    private ConfiguredCommandMasterProvisioningService getConfigCommand() throws IOException {
        Map<String, String> properties = Maps.newHashMap();
        properties.put("metaNectar.endpoint", "http://localhost:8080");
        properties.put("metaNectar.isMasterProvisioning", "true");
        properties.put("metaNectar.master.provisioning.basePort", "8080");
        properties.put("metaNectar.master.provisioning.homeLocation", getHomeDir().toString());
        properties.put("metaNectar.master.provisioning.timeOut", "2");
        properties.put("metaNectar.master.provisioning.script.provision", getProvisionScript());
        properties.put("metaNectar.master.provisioning.script.terminate", getTermianteScript());

        Properties ps = new Properties();
        ps.putAll(properties);
        return new ConfiguredCommandMasterProvisioningService(new Config(ps));
    }

    private CommandMasterProvisioningService getDefaultCommand() throws IOException {
        return getCommand(
                getProvisionScript(),
                getTermianteScript());
    }

    private CommandMasterProvisioningService getCommand(String provisionCommand, String terminateCommand) throws IOException {
        return new CommandMasterProvisioningService(8080, getHomeDir().toString(), 2,
                provisionCommand,
                terminateCommand);
    }

    private String getProvisionScript() {
        return "echo \"master_endpoint:" + getTestUrl() + "\"";
    }

    private String getTermianteScript() throws IOException {
        return "rm " + getTerminatingFile().toString();
    }

    private MasterServer provision() throws Exception {
        ProvisionListener pl = new ProvisionListener(2);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(MetaNectar.GRANT_PROPERTY, "grant");
        MasterServer ms = metaNectar.createMasterServer("org1");
        metaNectar.masterProvisioner.provision(ms, new URL("http://test/"), properties);

        pl.await(1, TimeUnit.MINUTES);

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
        TerminateListener tl = new TerminateListener(2);

        metaNectar.masterProvisioner.terminate(ms, false);

        tl.await(1, TimeUnit.MINUTES);

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
