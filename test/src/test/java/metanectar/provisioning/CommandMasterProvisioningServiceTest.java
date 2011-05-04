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
import java.util.concurrent.TimeUnit;

import static metanectar.provisioning.LatchMasterServerListener.ProvisionAndStartListener;
import static metanectar.provisioning.LatchMasterServerListener.StopAndTerminateListener;

/**
 * TODO, these tests most likely only work on unix unless commands are available on windows.
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
        DumbSlave slave = createSlave(metaNectar.masterProvisioner.getLabel());
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
        DumbSlave slave = createSlave(metaNectar.masterProvisioner.getLabel());
        slave.getNodeProperties().add(new MasterProvisioningNodeProperty(1, getDefaultCommand()));
        Computer computer = slave.toComputer();
        computer.connect(false).get();

        terminate(provision());
    }

    public void testProvisionCommandTimeOut() throws Exception {
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(1,
                getCommand("sleep 60", getStartScript(), getStopScript(), getTerminateScript())));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        _testProvisionStartTimeOut(new LatchMasterServerListener(1) {
                public void onProvisioningError(MasterServer ms) {
                    countDown();
                }
            }, MasterServer.State.ProvisioningError);

        assertFalse(getMasterHomeDir().exists());
        assertFalse(new File(getMasterHomeDir(), "started").exists());
    }

    public void testStartCommandTimeOut() throws Exception {
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(1,
                getCommand(getProvisionScript(), "sleep 60", getStopScript(), getTerminateScript())));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        _testProvisionStartTimeOut(new LatchMasterServerListener(1) {
                public void onStartingError(MasterServer ms) {
                    countDown();
                }
            }, MasterServer.State.StartingError);

        assertTrue(getMasterHomeDir().exists());
        assertFalse(new File(getMasterHomeDir(), "started").exists());
    }

    private void _testProvisionStartTimeOut(LatchMasterServerListener error, MasterServer.State state) throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(MetaNectar.GRANT_PROPERTY, "grant");
        MasterServer ms = metaNectar.createMasterServer("org1");
        metaNectar.masterProvisioner.provisionAndStart(ms, new URL("http://test/"), properties);

        error.await(1, TimeUnit.MINUTES);

        assertEquals(state, ms.getState());
        assertEquals(CommandMasterProvisioningService.CommandProvisioningError.class, ms.getError().getClass());
    }

    public void testStopCommandTimeOut() throws Exception {
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(1,
                getCommand(getProvisionScript(), getStartScript(), "sleep 60", getTerminateScript())));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        _testStopTerminateTimeOut(new LatchMasterServerListener(1) {
                public void onStoppingError(MasterServer ms) {
                    countDown();
                }
            }, MasterServer.State.StoppingError);

        assertTrue(getMasterHomeDir().exists());
        assertTrue(new File(getMasterHomeDir(), "started").exists());
    }

    public void testTerminateCommandTimeOut() throws Exception {
        metaNectar.getGlobalNodeProperties().add(new MasterProvisioningNodeProperty(1,
                getCommand(getProvisionScript(), getStartScript(), getStopScript(), "sleep 60")));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        _testStopTerminateTimeOut(new LatchMasterServerListener(1) {
                public void onTerminatingError(MasterServer ms) {
                    countDown();
                }
            }, MasterServer.State.TerminatingError);

        assertTrue(getMasterHomeDir().exists());
        assertFalse(new File(getMasterHomeDir(), "started").exists());
    }

    private void _testStopTerminateTimeOut(LatchMasterServerListener error, MasterServer.State state) throws Exception {
        MasterServer ms = provision();

        LatchMasterServerListener terminatingError = new LatchMasterServerListener(1) {
            public void onTerminatingError(MasterServer ms) {
                countDown();
            }
        };

        metaNectar.masterProvisioner.stopAndTerminate(ms, false);

        error.await(1, TimeUnit.MINUTES);

        assertEquals(state, ms.getState());
        assertEquals(CommandMasterProvisioningService.CommandProvisioningError.class, ms.getError().getClass());
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

    private String getMasterName() {
        return "org1";
    }

    private File masterHomeDir;

    private File getMasterHomeDir() throws IOException {
        if (masterHomeDir == null) {
            masterHomeDir = new File(getHomeDir(), getMasterName());
            masterHomeDir.deleteOnExit();
        }

        return masterHomeDir;
    }

    private ConfiguredCommandMasterProvisioningService getConfigCommand() throws IOException {
        Map<String, String> properties = Maps.newHashMap();
        properties.put("metaNectar.endpoint", "http://localhost:8080");
        properties.put("metaNectar.isMasterProvisioning", "true");
        properties.put("metaNectar.master.provisioning.basePort", "8080");
        properties.put("metaNectar.master.provisioning.homeLocation", getHomeDir().toString());
        properties.put("metaNectar.master.provisioning.timeOut", "2");
        properties.put("metaNectar.master.provisioning.script.provision", getProvisionScript());
        properties.put("metaNectar.master.provisioning.script.start", getStartScript());
        properties.put("metaNectar.master.provisioning.script.stop", getStopScript());
        properties.put("metaNectar.master.provisioning.script.terminate", getTerminateScript());

        Properties ps = new Properties();
        ps.putAll(properties);
        return new ConfiguredCommandMasterProvisioningService(new Config(ps));
    }

    private CommandMasterProvisioningService getDefaultCommand() throws IOException {
        return getCommand(
                getProvisionScript(),
                getStartScript(),
                getStopScript(),
                getTerminateScript());
    }

    private CommandMasterProvisioningService getCommand(String provisionCommand, String startCommand,
                                                        String stopCommand, String terminateCommand) throws IOException {
        return new CommandMasterProvisioningService(8080, getHomeDir().toString(), 2,
                provisionCommand,
                startCommand,
                stopCommand,
                terminateCommand);
    }

    private String getProvisionScript() throws IOException {
        return String.format("/bin/sh -c \"mkdir -p '%s'; echo 'MASTER_ENDPOINT=%s'\"", getMasterHomeDir().toString(), getTestUrl());
    }

    private String getStartScript() throws IOException {
        return "touch " + getMasterHomeDir().toString() + "/started";
    }

    private String getStopScript() throws IOException {
        return "rm " + getMasterHomeDir().toString() + "/started";
    }

    private String getTerminateScript() throws IOException {
        return "rm -fr " + getMasterHomeDir().toString();
    }

    private MasterServer provision() throws Exception {
        ProvisionAndStartListener pl = new ProvisionAndStartListener(4);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(MetaNectar.GRANT_PROPERTY, "grant");
        MasterServer ms = metaNectar.createMasterServer(getMasterName());
        metaNectar.masterProvisioner.provisionAndStart(ms, new URL("http://test/"), properties);

        pl.await(1, TimeUnit.MINUTES);

        Map<String, String> params = getParams(ms.getEndpoint());

        String port = params.get(CommandMasterProvisioningService.Variable.MASTER_PORT.toString());
        assertNotNull(port);
        assertEquals(8080, Integer.valueOf(port) + ms.getId());

        String home = params.get(CommandMasterProvisioningService.Variable.MASTER_HOME.toString());
        assertNotNull(home);
        assertEquals(home, getMasterHomeDir().toString());

        String metaNectarEndpoint = params.get(CommandMasterProvisioningService.Variable.MASTER_METANECTAR_ENDPOINT.toString());
        assertNotNull(metaNectarEndpoint);
        assertEquals(metaNectarEndpoint, new URL("http://test/").toExternalForm());

        String grantId = params.get(CommandMasterProvisioningService.Variable.MASTER_GRANT_ID.toString());
        assertNotNull(grantId);
        assertEquals(grantId, "grant");

        assertTrue(getMasterHomeDir().exists());
        assertTrue(new File(getMasterHomeDir(), "started").exists());

        return ms;
    }

    private void terminate(MasterServer ms) throws Exception {
        StopAndTerminateListener tl = new StopAndTerminateListener(4);

        metaNectar.masterProvisioner.stopAndTerminate(ms, false);

        tl.await(1, TimeUnit.MINUTES);

        assertFalse(getMasterHomeDir().exists());
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
