package metanectar.provisioning;

import com.google.common.collect.Maps;
import hudson.*;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.LocalChannel;
import hudson.remoting.VirtualChannel;
import metanectar.Config;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A master provisioning service that executes commands to provision and terminate masters.
 *
 * @author Paul Sandoz
 */
public class CommandMasterProvisioningService extends MasterProvisioningService {

    public static class CommandProvisioningError extends IOException {
        final int exitCode;

        public CommandProvisioningError(String s, int exitCode) {
            super(s);
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }
    }

    public enum Variable {
        MASTER_INDEX,
        MASTER_NAME,
        MASTER_HOME_LOCATION,
        MASTER_PORT,
        MASTER_JAVA_DEBUG_PORT,
        MASTER_METANECTAR_ENDPOINT,
        MASTER_GRANT_ID,
        MASTER_SNAPSHOT
    }

    private enum Property {
        MASTER_ENDPOINT,
        MASTER_HOME,
        MASTER_SNAPSHOT
    }

    private final int basePort;

    private final String homeLocation;

    private final int timeOut;

    private final String provisionCommand;

    private final String startCommand;

    private final String stopCommand;

    private final String terminateCommand;

    @DataBoundConstructor
    public CommandMasterProvisioningService(int basePort, String homeLocation, int timeOut,
                                            String provisionCommand, String startCommand, String stopCommand, String terminateCommand) {
        this.basePort = basePort;
        this.homeLocation = homeLocation;
        this.timeOut = timeOut;
        this.provisionCommand = provisionCommand;
        this.startCommand = startCommand;
        this.stopCommand = stopCommand;
        this.terminateCommand = terminateCommand;
    }

    public int getBasePort() {
        return basePort;
    }

    public String getHomeLocation() {
        return homeLocation;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public String getProvisionCommand() {
        return provisionCommand;
    }

    public String getStartCommand() {
        return startCommand;
    }

    public String getStopCommand() {
        return stopCommand;
    }

    public String getTerminateCommand() {
        return terminateCommand;
    }

    private int getPort(int id) {
        return basePort + id;
    }

    private Map<String, String> getVariables(MasterServer ms) {
        final Map<String, String> variables = Maps.newHashMap();
        variables.put(Variable.MASTER_INDEX.name(), Integer.toString(ms.getId()));
        variables.put(Variable.MASTER_NAME.name(), ms.getEncodedName());
        if (homeLocation != null)
            variables.put(Variable.MASTER_HOME_LOCATION.name(), homeLocation);

        return variables;
    }

    @Override
    public Future<Provisioned> provision(final MasterServer ms, final URL metaNectarEndpoint, final Map<String, Object> properties) throws Exception {
        final VirtualChannel channel = ms.getNode().toComputer().getChannel();
        final TaskListener listener = ms.getTaskListener();

        final Map<String, String> provisionVariables = getVariables(ms);
        provisionVariables.put(Variable.MASTER_PORT.name(), Integer.toString(getPort(ms.getNodeId())));
        provisionVariables.put(Variable.MASTER_METANECTAR_ENDPOINT.name(), metaNectarEndpoint.toExternalForm());
        provisionVariables.put(Variable.MASTER_GRANT_ID.name(), ms.getGrantId());
        if (ms.getSnapshot() != null) {
            provisionVariables.put(Variable.MASTER_SNAPSHOT.name(), ms.getSnapshot().toExternalForm());
        }

        // Add Java debug port, if configured
        Config.JavaDebugProperties dps = MetaNectar.getInstance().getConfig().getBean(Config.JavaDebugProperties.class);
        if (dps.getJavaDebugBasePort() != -1) {
            provisionVariables.put(Variable.MASTER_JAVA_DEBUG_PORT.name(), Integer.toString(dps.getJavaDebugBasePort() + ms.getNodeId()));
        }

        return Computer.threadPoolForRemoting.submit(new Callable<Provisioned>() {
            public Provisioned call() throws Exception {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();

                listener.getLogger().println(String.format("Executing provisioning command \"%s\" with environment variables %s", getProvisionCommand(), provisionVariables));
                final Proc provisionProcess = getLauncher(channel, listener).launch().
                        envs(provisionVariables).
                        cmds(Util.tokenize(getProvisionCommand())).
                        stderr(listener.getLogger()).
                        stdout(baos).
                        start();

                final int result = provisionProcess.joinWithTimeout(timeOut, TimeUnit.SECONDS, listener);

                if (result != 0) {
                    throw commandError(listener, "Failed to provision master, received signal from provision command: " + result, result);
                }

                final Properties properties = new Properties();
                try {
                    properties.load(new ByteArrayInputStream(baos.toByteArray()));
                } catch (IOException e) {
                    e.printStackTrace(listener.error("Error parsing provisioning command result into Java properties"));
                    throw e;
                }

                listener.getLogger().println("Provisioning command succeeded and returned with the properties: " + properties);

                if (!properties.containsKey(Property.MASTER_HOME.toString())) {
                    String msg = "The returned properties does not contain the required property \"" + Property.MASTER_HOME.toString() + "\"";
                    listener.error(msg);
                    throw new IOException(msg);
                }

                if (!properties.containsKey(Property.MASTER_ENDPOINT.toString())) {
                    String msg = "The returned properties does not contain the required property \"" + Property.MASTER_ENDPOINT.toString() + "\"";
                    listener.error(msg);
                    throw new IOException(msg);
                }

                final Provisioned provisioned;
                try {
                    provisioned = new Provisioned(
                            properties.getProperty(Property.MASTER_HOME.toString()),
                            new URL(properties.getProperty(Property.MASTER_ENDPOINT.toString())));
                } catch (MalformedURLException e) {
                    e.printStackTrace(listener.error(String.format("The property \"%s\" of value \"%s\" is not a valid", Property.MASTER_ENDPOINT.toString(), properties.get(Property.MASTER_ENDPOINT.toString()))));
                    throw e;
                }

                // additional provisioning of home directory
                final HomeDirectoryProvisioner hdp = new HomeDirectoryProvisioner(listener, getFilePath(channel, provisioned.getHome()));

                return provisioned;
            }
        });
    }

    public Future<?> start(final MasterServer ms) throws Exception {
        final VirtualChannel channel = ms.getNode().toComputer().getChannel();
        final TaskListener listener = ms.getTaskListener();

        final Map<String, String> startVariables = getVariables(ms);

        return Computer.threadPoolForRemoting.submit(new Callable<Void>() {
            public Void call() throws Exception {
                listener.getLogger().println(String.format("Executing start command \"%s\" with environment variables %s", getStartCommand(), startVariables));
                final Proc startProcess = getLauncher(channel, listener).launch().
                        envs(startVariables).
                        cmds(Util.tokenize(getStartCommand())).
                        stderr(listener.getLogger()).
                        stdout(listener.getLogger()).
                        start();

                final int result = startProcess.joinWithTimeout(timeOut, TimeUnit.SECONDS, listener);

                if (result != 0) {
                    throw commandError(listener, "Failed to start master, received signal from start command: " + result, result);
                }

                listener.getLogger().println("Start command succeeded");
                return null;
            }
        });
    }

    public Future<?> stop(final MasterServer ms) throws Exception {
        final VirtualChannel channel = ms.getNode().toComputer().getChannel();
        final TaskListener listener = ms.getTaskListener();

        final Map<String, String> variables = getVariables(ms);

        return Computer.threadPoolForRemoting.submit(new Callable<Void>() {
            public Void call() throws Exception {
                listener.getLogger().println(String.format("Executing stop command \"%s\" with environment variables %s", getStopCommand(), variables));
                final Proc proc = getLauncher(channel, listener).launch().
                        envs(variables).
                        cmds(Util.tokenize(getStopCommand())).
                        stderr(listener.getLogger()).
                        stdout(listener.getLogger()).
                        start();

                final int result = proc.joinWithTimeout(timeOut, TimeUnit.SECONDS, listener);

                if (result != 0) {
                    throw commandError(listener, "Failed to stop master, received signal from stop command: " + result, result);
                }

                listener.getLogger().println("Stop command succeeded");
                return null;
            }
        });
    }

    @Override
    public Future<Terminated> terminate(final MasterServer ms) throws Exception {
        final VirtualChannel channel = ms.getNode().toComputer().getChannel();
        final TaskListener listener = ms.getTaskListener();

        final Map<String, String> variables = getVariables(ms);

        return Computer.threadPoolForRemoting.submit(new Callable<Terminated>() {
            public Terminated call() throws Exception {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();

                listener.getLogger().println(String.format("Executing termination command \"%s\" with environment variables %s", getTerminateCommand(), variables));
                final Proc proc = getLauncher(channel, listener).launch().
                        envs(variables).
                        cmds(Util.tokenize(getTerminateCommand())).
                        stderr(listener.getLogger()).
                        stdout(baos).
                        start();

                final int result = proc.joinWithTimeout(timeOut, TimeUnit.SECONDS, listener);

                if (result != 0) {
                    throw commandError(listener, "Failed to terminate master, received signal from terminate command: " + result, result);
                }

                final Properties properties = new Properties();
                try {
                    properties.load(new ByteArrayInputStream(baos.toByteArray()));
                } catch (IOException e) {
                    e.printStackTrace(listener.error("Error parsing termination command result into Java properties"));
                    throw e;
                }

                listener.getLogger().println("Termination command succeeded and returned with the properties: " + properties);

                if (!properties.containsKey(Property.MASTER_SNAPSHOT.toString())) {
                    String msg = "The returned properties does not contain the required property \"" + Property.MASTER_SNAPSHOT.toString() + "\"";
                    listener.error(msg);
                    throw new IOException(msg);
                }

                try {
                    return new Terminated(new URL(properties.getProperty(Property.MASTER_SNAPSHOT.toString())));
                } catch (MalformedURLException e) {
                    e.printStackTrace(listener.error(String.format("The property \"%s\" of value \"%s\" is not a valid", Property.MASTER_ENDPOINT.toString(), properties.get(Property.MASTER_ENDPOINT.toString()))));
                    throw e;
                }
            }
        });
    }

    private CommandProvisioningError commandError(TaskListener listener, String message, int result) {
        listener.error(message);
        return new CommandProvisioningError(message, result);
    }

    private Launcher getLauncher(final VirtualChannel channel, final TaskListener listener) throws Exception {
        return (channel instanceof LocalChannel)
                ? new Launcher.LocalLauncher(listener, channel)
                : new Launcher.RemoteLauncher(listener, channel, channel.call(new IsUnix()));
    }

    private FilePath getFilePath(final VirtualChannel channel, String path) {
        return (channel instanceof LocalChannel)
                ? new FilePath(new File(path))
                : new FilePath(channel, path);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MasterProvisioningService> {
        public String getDisplayName() {
            return "Command Provisioning Service";
        }
    }

    private static final class IsUnix implements hudson.remoting.Callable<Boolean,IOException> {
        public Boolean call() throws IOException {
            return File.pathSeparatorChar==':';
        }
        private static final long serialVersionUID = 1L;
    }

}
