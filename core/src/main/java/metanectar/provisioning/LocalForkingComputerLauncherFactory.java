package metanectar.provisioning;

import com.cloudbees.commons.metanectar.provisioning.ComputerLauncherFactory;
import hudson.EnvVars;
import hudson.Functions;
import hudson.Util;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.CommandLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.Messages;
import hudson.slaves.SlaveComputer;
import hudson.util.ProcessTree;
import hudson.util.StreamCopyThread;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Computer launcher which will launch the slave as a forked process on the master.
 */
public class LocalForkingComputerLauncherFactory extends ComputerLauncherFactory {
    private final String nodeName;
    private final int numExecutors;
    private final String labelString;
    private transient LocalForkingComputerLauncher launcher;

    public LocalForkingComputerLauncherFactory(String nodeName, int numExecutors, String labelString) {
        this.nodeName = nodeName;
        this.numExecutors = numExecutors;
        this.labelString = labelString;
    }

    @Override
    public String getNodeName() {
        return nodeName;
    }

    @Override
    public String getRemoteFS() {
        return getRemoteFSDir().getAbsolutePath();
    }

    private File getRemoteFSDir() {
        return new File(new File(Hudson.getInstance().getRootDir(), "slaves"), nodeName);
    }

    @Override
    public int getNumExecutors() {
        return numExecutors;
    }

    @Override
    public String getLabelString() {
        return labelString;
    }

    public synchronized ComputerLauncher getOrCreateLauncher() throws IOException, InterruptedException {
        if (launcher == null) {
            launcher = new LocalForkingComputerLauncher();
        }
        return launcher;
    }

    /**
     * {@link ComputerLauncher} through a remote login mechanism like ssh/rsh.
     *
     * @author Stephen Connolly
     * @author Kohsuke Kawaguchi
     */
    private class LocalForkingComputerLauncher extends ComputerLauncher {

        public LocalForkingComputerLauncher() {
        }

        /**
         * Gets the formatted current time stamp.
         */
        private String getTimestamp() {
            return String.format("[%1$tD %1$tT]", new Date());
        }

        @Override
        public void launch(SlaveComputer computer, final TaskListener listener) {
                File remoteFSDir = getRemoteFSDir();
            if (!remoteFSDir.isDirectory())
                remoteFSDir.mkdirs();
            String javaHome = System.getProperty("java.home");
            String javaBinary = Functions.isWindows() ? "java.exe" : "java";
            String java = new File(new File(javaHome, "bin"), javaBinary).getAbsolutePath();
            String slaveJar;
            try {
                slaveJar = new File(Hudson.getInstance().getJnlpJars("slave.jar").getURL().toURI()).getAbsolutePath();
            } catch (URISyntaxException e) {
                e.printStackTrace(listener.getLogger());
                return;
            } catch (MalformedURLException e) {
                e.printStackTrace(listener.getLogger());
                return;
            }
            EnvVars _cookie = null;
            Process _proc = null;
            try {
                listener.getLogger().println(hudson.model.Messages.Slave_Launching(getTimestamp()));
                listener.getLogger().println("[" + remoteFSDir.getAbsolutePath()+"] $ " + java + " -jar " + slaveJar);

                ProcessBuilder pb = new ProcessBuilder(java, "-jar", slaveJar);
                pb.directory(remoteFSDir);
                final EnvVars cookie = _cookie = EnvVars.createCookie();
                pb.environment().putAll(cookie);

                {// system defined variables
                    String rootUrl = Hudson.getInstance().getRootUrl();
                    if (rootUrl != null) {
                        pb.environment().put("HUDSON_URL", rootUrl);    // for backward compatibility
                        pb.environment().put("JENKINS_URL", rootUrl);
                        pb.environment().put("SLAVEJAR_URL", rootUrl + "/jnlpJars/slave.jar");
                    }
                }

                final Process proc = _proc = pb.start();

                // capture error information from stderr. this will terminate itself
                // when the process is killed.
                new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(),
                        proc.getErrorStream(), listener.getLogger()).start();

                computer.setChannel(proc.getInputStream(), proc.getOutputStream(), listener.getLogger(),
                        new Channel.Listener() {
                            @Override
                            public void onClosed(Channel channel, IOException cause) {
                                try {
                                    int exitCode = proc.exitValue();
                                    if (exitCode != 0) {
                                        listener.error("Process terminated with exit code " + exitCode);
                                    }
                                } catch (IllegalThreadStateException e) {
                                    // hasn't terminated yet
                                }

                                try {
                                    ProcessTree.get().killAll(proc, cookie);
                                } catch (InterruptedException e) {
                                    LOGGER.log(Level.INFO, "interrupted", e);
                                }
                            }
                        });

                LOGGER.info("slave agent launched for " + computer.getDisplayName());
            } catch (InterruptedException e) {
                e.printStackTrace(listener.error(Messages.ComputerLauncher_abortedLaunch()));
            } catch (RuntimeException e) {
                e.printStackTrace(listener.error(Messages.ComputerLauncher_unexpectedError()));
            } catch (Error e) {
                e.printStackTrace(listener.error(Messages.ComputerLauncher_unexpectedError()));
            } catch (IOException e) {
                Util.displayIOException(e, listener);

                String msg = Util.getWin32ErrorMessage(e);
                if (msg == null) {
                    msg = "";
                } else {
                    msg = " : " + msg;
                }
                msg = hudson.model.Messages.Slave_UnableToLaunch(computer.getDisplayName(), msg);
                LOGGER.log(Level.SEVERE, msg, e);
                e.printStackTrace(listener.error(msg));

                if (_proc != null) {
                    try {
                        ProcessTree.get().killAll(_proc, _cookie);
                    } catch (InterruptedException x) {
                        x.printStackTrace(listener.error(Messages.ComputerLauncher_abortedLaunch()));
                    }
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CommandLauncher.class.getName());

}
