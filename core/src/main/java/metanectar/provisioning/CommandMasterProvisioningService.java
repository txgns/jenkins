package metanectar.provisioning;

import com.google.common.collect.Maps;
import hudson.*;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.LocalChannel;
import hudson.remoting.VirtualChannel;
import hudson.util.IOException2;
import metanectar.model.MetaNectar;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A master provisioning service that executes commands to provision and terminate masters.
 * <p>
 * The command to provision a master must return a URL on stdout, if the command is successful.
 * <p>
 * TODO change MasterProvisioningService to support provision, start, stop, terminate and
 *      then change MasterProvisioner to execute multiple tasks
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
        MASTER_PORT,
        MASTER_HOME,
        MASTER_METANECTAR_ENDPOINT,
        MASTER_GRANT_ID
    }

    private enum Property {
        MASTER_ENDPOINT
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

    private String getHome(String organization) {
        return (homeLocation.endsWith("/"))
                ? homeLocation + organization : homeLocation + "/" + organization;
    }

    private int getPort(int id) {
        return basePort + id;
    }

    @Override
    public Future<Master> provision(final VirtualChannel channel, final TaskListener listener,
                                    final int id, final String organization, final URL metaNectarEndpoint, final Map<String, Object> properties) throws Exception {
        final String home = getHome(organization);

        final Map<String, String> provisionVariables = Maps.newHashMap();
        provisionVariables.put(Variable.MASTER_PORT.toString(), Integer.toString(getPort(id)));
        provisionVariables.put(Variable.MASTER_HOME.toString(), home);
        provisionVariables.put(Variable.MASTER_METANECTAR_ENDPOINT.toString(), metaNectarEndpoint.toExternalForm());
        provisionVariables.put(Variable.MASTER_GRANT_ID.toString(), (String)properties.get(MetaNectar.GRANT_PROPERTY));

        final Map<String, String> startVariables = Maps.newHashMap();
        startVariables.put(Variable.MASTER_HOME.toString(), home);

        return Computer.threadPoolForRemoting.submit(new Callable<Master>() {
            public Master call() throws Exception {
                Master m;

                // provision command
                {
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


                    Properties properties = new Properties();
                    try {
                        properties.load(new ByteArrayInputStream(baos.toByteArray()));
                    } catch (IOException e) {
                        e.printStackTrace(listener.error("Error parsing provisioning command result into Java properties"));
                        throw e;
                    }

                    listener.getLogger().println("Provisioning command succeeded and returned with the properties: " + properties);

                    if (!properties.containsKey(Property.MASTER_ENDPOINT.toString())) {
                        String msg = "The returned properties does not contain the required property \"" + Property.MASTER_ENDPOINT.toString() + "\"";
                        listener.error(msg);
                        throw new IOException(msg);
                    }

                    try {
                        final URL endpoint = new URL(properties.getProperty(Property.MASTER_ENDPOINT.toString()));

                        m = new Master(organization, endpoint);
                    } catch (MalformedURLException e) {
                        e.printStackTrace(listener.error(String.format("The property \"%s\" of value \"%s\" is not a valid", Property.MASTER_ENDPOINT.toString(), properties.get(Property.MASTER_ENDPOINT.toString()))));
                        throw e;
                    }
                }

                // additional provisioning of home directory
                {
                    final HomeDirectoryProvisioner hdp = new HomeDirectoryProvisioner(listener, getFilePath(channel, home));
                }

                // start command
                {
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
                }

                return m;
            }
        });
    }

    @Override
    public Future<?> terminate(final VirtualChannel channel, final TaskListener listener,
                               final String organization, boolean clean) throws Exception {
        final Map<String, String> variables = Maps.newHashMap();
        variables.put(Variable.MASTER_HOME.toString(), getHome(organization));

        return Computer.threadPoolForRemoting.submit(new Callable<Void>() {
            public Void call() throws Exception {

                // stop command
                {
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
                }

                // terminate command
                {
                    listener.getLogger().println(String.format("Executing termination command \"%s\" with environment variables %s", getTerminateCommand(), variables));
                    final Proc proc = getLauncher(channel, listener).launch().
                            envs(variables).
                            cmds(Util.tokenize(getTerminateCommand())).
                            stderr(listener.getLogger()).
                            stdout(listener.getLogger()).
                            start();

                    final int result = proc.joinWithTimeout(timeOut, TimeUnit.SECONDS, listener);

                    if (result != 0) {
                        throw commandError(listener, "Failed to terminate master, received signal from terminate command: " + result, result);
                    }

                    listener.getLogger().println("Termination command succeeded");
                }

                return null;
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
