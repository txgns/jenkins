package metanectar.provisioning;

import com.google.common.collect.Maps;
import hudson.model.Computer;
import hudson.slaves.DumbSlave;
import metanectar.Config;
import metanectar.model.MasterServer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
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
public class CommandMasterProvisioningServiceTest extends AbstractMasterProvisioningTestCase {
    public void testConfigProvisionLocally() throws Exception {
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(1, getConfigCommand()));
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
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(1, getDefaultCommand()));
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

    public void testSnapshot() throws Exception {
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(1, getDefaultCommand()));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        provision(terminate(provision()));
    }

    public void testProvisionCommandTimeOut() throws Exception {
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(1,
                getCommand("sleep 60", getStartScript(), getStopScript(), getTerminateScript())));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        MasterServer ms = _testProvisionStartTimeOut(new LatchMasterServerListener(1) {
                public void onProvisioningError(MasterServer ms) {
                    countDown();
                }
            }, MasterServer.State.ProvisioningError);

        assertFalse(getMasterHomeDir().exists());
        assertFalse(new File(getMasterHomeDir(), "started").exists());

        assertEquals(1, metaNectar.masterProvisioner.getProvisionedMasters().size());
        assertTrue(metaNectar.masterProvisioner.getProvisionedMasters().get(metaNectar).contains(ms));

        // Try again
        _testProvisionStartTimeOut(ms, new LatchMasterServerListener(1) {
                public void onProvisioningError(MasterServer ms) {
                    countDown();
                }
            }, MasterServer.State.ProvisioningError);

        assertEquals(1, metaNectar.masterProvisioner.getProvisionedMasters().size());
        assertTrue(metaNectar.masterProvisioner.getProvisionedMasters().get(metaNectar).contains(ms));
    }

    public void testStartCommandTimeOut() throws Exception {
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(1,
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

    private MasterServer _testProvisionStartTimeOut(LatchMasterServerListener error, MasterServer.State state) throws Exception {
        return _testProvisionStartTimeOut(metaNectar.createManagedMaster("org1"), error, state);
    }

    private MasterServer _testProvisionStartTimeOut(MasterServer ms, LatchMasterServerListener error, MasterServer.State state) throws Exception {
        ms.provisionAndStartAction();

        error.await(1, TimeUnit.MINUTES);

        assertEquals(state, ms.getState());
        assertEquals(CommandMasterProvisioningService.CommandProvisioningError.class, ms.getError().getClass());
        return ms;
    }

    public void testStopCommandTimeOut() throws Exception {
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(1,
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
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(1,
                getCommand(getProvisionScript(), getStartScript(), getStopScript(), "sleep 60")));
        // Reset the labels
        metaNectar.setNodes(metaNectar.getNodes());

        MasterServer ms = _testStopTerminateTimeOut(new LatchMasterServerListener(1) {
                public void onTerminatingError(MasterServer ms) {
                    countDown();
                }
            }, MasterServer.State.TerminatingError);

        assertTrue(getMasterHomeDir().exists());
        assertFalse(new File(getMasterHomeDir(), "started").exists());

        assertEquals(1, metaNectar.masterProvisioner.getProvisionedMasters().size());
        assertTrue(metaNectar.masterProvisioner.getProvisionedMasters().get(metaNectar).contains(ms));

        // Try again
        _testTerminateTimeOut(ms, new LatchMasterServerListener(1) {
                public void onTerminatingError(MasterServer ms) {
                    countDown();
                }
            }, MasterServer.State.TerminatingError);

        assertTrue(getMasterHomeDir().exists());
        assertFalse(new File(getMasterHomeDir(), "started").exists());

        assertEquals(1, metaNectar.masterProvisioner.getProvisionedMasters().size());
        assertTrue(metaNectar.masterProvisioner.getProvisionedMasters().get(metaNectar).contains(ms));

        // Force termination
        _testForceTerminateTimeOut(ms, new LatchMasterServerListener(2) {
                public void onTerminatingError(MasterServer ms) {
                    countDown();
                }

                public void onTerminated(MasterServer ms) {
                    countDown();
                }
            }, MasterServer.State.Terminated);

        assertEquals(0, metaNectar.masterProvisioner.getProvisionedMasters().size());
        assertFalse(metaNectar.masterProvisioner.getProvisionedMasters().get(metaNectar).contains(ms));
    }

    private MasterServer _testStopTerminateTimeOut(LatchMasterServerListener error, MasterServer.State state) throws Exception {
        MasterServer ms = provision();

        ms.stopAndTerminateAction();

        error.await(1, TimeUnit.MINUTES);

        assertEquals(state, ms.getState());
        assertEquals(CommandMasterProvisioningService.CommandProvisioningError.class, ms.getError().getClass());

        return ms;
    }

    private void _testTerminateTimeOut(MasterServer ms, LatchMasterServerListener error, MasterServer.State state) throws Exception {
        ms.terminateAction(false);

        error.await(1, TimeUnit.MINUTES);

        assertEquals(state, ms.getState());
        assertEquals(CommandMasterProvisioningService.CommandProvisioningError.class, ms.getError().getClass());
    }

    private void _testForceTerminateTimeOut(MasterServer ms, LatchMasterServerListener error, MasterServer.State state) throws Exception {
        ms.terminateAction(true);

        error.await(1, TimeUnit.MINUTES);

        assertEquals(state, ms.getState());
    }

    private MasterServer ms;

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

    private int getMasterIndex() {
        return 0;
    }

    private String getMasterName() {
        return "org";
    }

    private String getMasterIdName() {
        return MasterServer.createIdName(getMasterIndex(), getMasterName());
    }

    private File masterHomeDir;

    private File getMasterHomeDir() throws IOException {
        if (masterHomeDir == null) {
            masterHomeDir = new File(getHomeDir(), getMasterIdName());
            masterHomeDir.deleteOnExit();
        }

        return masterHomeDir;
    }

    private URL snapshotUrl;

    private URL getSnapshotUrl() throws IOException {
        if (snapshotUrl == null) {

            File f = File.createTempFile("terminate-snapshot-", ".zip");
            snapshotUrl = new URL("file", null, f.getAbsolutePath());
        }

        return snapshotUrl;
    }

    private ConfiguredCommandMasterProvisioningService getConfigCommand() throws IOException {
        Map<String, String> properties = Maps.newHashMap();
        properties.put("metaNectar.endpoint", "http://localhost:8080");
        properties.put("metaNectar.isMasterProvisioning", "true");
        properties.put("metaNectar.master.provisioning.basePort", "8080");
        properties.put("metaNectar.master.provisioning.homeLocation", getHomeDir().toString());
        properties.put("metaNectar.master.provisioning.timeOut", "2");
        properties.put("metaNectar.master.provisioning.archive", getHomeDir().toString());
        properties.put("metaNectar.master.provisioning.script.provision", getProvisionScript());
        properties.put("metaNectar.master.provisioning.script.start", getStartScript());
        properties.put("metaNectar.master.provisioning.script.stop", getStopScript());
        properties.put("metaNectar.master.provisioning.script.terminate", getTerminateScript());

        Properties ps = new Properties();
        ps.putAll(properties);
        Config c = new Config(ps);
        metaNectar.setConfig(c);
        return new ConfiguredCommandMasterProvisioningService(c);
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
                getHomeDir().toString(),
                provisionCommand,
                startCommand,
                stopCommand,
                terminateCommand);
    }

    private String getProvisionScript() throws IOException {
        return String.format("/bin/sh -c \"mkdir -p '%s'; echo 'MASTER_ENDPOINT=%s\nMASTER_HOME=%s'\"",
                getMasterHomeDir().toString(),
                getTestUrl(),
                getMasterHomeDir());
    }

    private String getStartScript() throws IOException {
        return "touch " + getMasterHomeDir().toString() + "/started";
    }

    private String getStopScript() throws IOException {
        return "rm " + getMasterHomeDir().toString() + "/started";
    }

    private String getTerminateScript() throws IOException {
        return String.format("/bin/sh -c \"rm -fr '%s'; echo 'MASTER_SNAPSHOT=%s'\"", getMasterHomeDir().toString(), getSnapshotUrl().toExternalForm());
    }

    private MasterServer provision() throws Exception {
        ProvisionAndStartListener pl = new ProvisionAndStartListener(4);

        ms = metaNectar.createManagedMaster(getMasterName());
        ms.provisionAndStartAction();

        pl.await(1, TimeUnit.MINUTES);

        assertNotNull(ms.getLocalHome());
        assertEquals(getMasterHomeDir().toString(), ms.getLocalHome());
        assertNotNull(ms.getLocalEndpoint());

        Map<String, String> params = getParams(ms.getLocalEndpoint());

        String port = params.get(CommandMasterProvisioningService.Variable.MASTER_PORT.toString());
        assertNotNull(port);
        assertEquals(8080, Integer.valueOf(port) + ms.getNodeId());

        String index = params.get(CommandMasterProvisioningService.Variable.MASTER_INDEX.toString());
        assertNotNull(index);
        assertEquals(getMasterIndex(), Integer.parseInt(index));

        String name = params.get(CommandMasterProvisioningService.Variable.MASTER_NAME.toString());
        assertNotNull(name);
        assertEquals(getMasterName(), name);

        String metaNectarEndpoint = params.get(CommandMasterProvisioningService.Variable.MASTER_METANECTAR_ENDPOINT.toString());
        assertNotNull(metaNectarEndpoint);
        assertEquals(metaNectar.getMetaNectarPortUrl().toExternalForm(), metaNectarEndpoint);

        String grantId = params.get(CommandMasterProvisioningService.Variable.MASTER_GRANT_ID.toString());
        assertNotNull(grantId);
        assertEquals(ms.getGrantId(), grantId);

        // Not set
        String snapshot = params.get(CommandMasterProvisioningService.Variable.MASTER_SNAPSHOT.toString());
        assertNotNull(snapshot);
        assertEquals("${" + CommandMasterProvisioningService.Variable.MASTER_SNAPSHOT.toString() + "}", snapshot);

        assertTrue(getMasterHomeDir().exists());
        assertTrue(new File(getMasterHomeDir(), "started").exists());

        return ms;
    }

    private MasterServer terminate(MasterServer ms) throws Exception {
        StopAndTerminateListener tl = new StopAndTerminateListener(4);

        ms.stopAndTerminateAction();

        tl.await(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.Terminated, ms.getState());

        assertNotNull(ms.getSnapshot());
        assertTrue(new File(ms.getSnapshot().getPath()).exists());
        assertFalse(new File(getSnapshotUrl().getPath()).exists());
        assertFalse(getMasterHomeDir().exists());

        return ms;
    }

    private MasterServer provision(MasterServer ms) throws Exception {
        ProvisionAndStartListener pl = new ProvisionAndStartListener(4);

        ms.provisionAndStartAction();

        pl.await(1, TimeUnit.MINUTES);

        Map<String, String> params = getParams(ms.getLocalEndpoint());

        String snapshot = params.get(CommandMasterProvisioningService.Variable.MASTER_SNAPSHOT.toString());
        assertNotNull(snapshot);
        assertEquals(ms.getSnapshot().toExternalForm(), snapshot);

        return ms;
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
