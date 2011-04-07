package metanectar.slave;

import com.cloudbees.commons.metanectar.agent.Agent;
import com.cloudbees.commons.metanectar.agent.AgentStatusListener;
import com.cloudbees.commons.metanectar.agent.MetaNectarAgentProtocol;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.remoting.Channel;
import hudson.model.Slave;
import hudson.remoting.VirtualChannel;
import hudson.slaves.*;
import metanectar.provisioning.Master;
import metanectar.provisioning.MasterProvisioningNodeProperty;
import metanectar.provisioning.MasterProvisioningService;
import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A master provisioning dumb slave.
 * <p>
 * At the moment this starts a new thread on the slave.jar that emulates a master.
 * <p>
 * This will be modified to start a nectar instance (via a daemon to avoid the process getting killed
 * if the slave.jar process dies).
 *
 *
 * @author Paul Sandoz
 */
public class MasterProvisioningSlave extends Slave {

    @DataBoundConstructor
    public MasterProvisioningSlave(String name, String nodeDescription, String remoteFS, String numExecutors, Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws IOException, Descriptor.FormException {
    	super(name, nodeDescription, remoteFS, numExecutors, mode, "_masters_", launcher, retentionStrategy, adapt(nodeProperties));
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {
        public String getDisplayName() {
            return "Master Provisioning Dumb Slave";
        }
    }

    static List<? extends NodeProperty<?>> adapt(List<? extends NodeProperty<?>> l) {
        List<NodeProperty<?>> copy = new ArrayList<NodeProperty<?>>(l);
        copy.add(new MasterProvisioningNodeProperty(4, new TestMasterProvisioningService(2000)));
        return copy;
    }


    public static class TestMasterProvisioningService implements MasterProvisioningService {

        private final int delay;

        TestMasterProvisioningService(int delay) {
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
                masterServer.setRetryInterval(2000);
                return masterServer.start();
            }
        }
    }

    public static class TestMasterServer {
        private static final Logger LOGGER = Logger.getLogger(TestMasterServer.class.getName());

        final URL metaNectarEndpoint;

        final String organization;

        final URL endpoint;

        final InstanceIdentity id;

        final Agent agent;

        class Client extends MetaNectarAgentProtocol.Listener {
            Channel channel;

            @Override
            public URL getEndpoint() throws IOException {
                return endpoint;
            }

            @Override
            public void onConnectingTo(URL address, X509Certificate identity, String organization, Map<String, String> properties) throws GeneralSecurityException, IOException {
                LOGGER.info("Connecting to: " + organization + " " + address);
            }

            @Override
            public void onConnectedTo(Channel channel, X509Certificate identity, String organization) throws IOException {
                LOGGER.info("Connected: " + channel);

                this.channel = channel;
                try {
                    channel.join();
                } catch (InterruptedException ex) {
                    LOGGER.log(Level.WARNING, "Channel was interrupted", ex);
                }
            }

            @Override
            public void onRefusal(MetaNectarAgentProtocol.GracefulConnectionRefusalException e) throws Exception {
                // Don't re-throw so that the agent keeps tr-trying
                LOGGER.log(Level.WARNING, "Server refused connection", e);
            }

            @Override
            public void onError(Exception e) throws Exception {
                // Don't re-throw so that the agent keeps tr-trying
                LOGGER.log(Level.SEVERE, "Error connecting to server", e);
            }
        }

        class Resolver implements Agent.ConnectionResolver {
            public InetSocketAddress resolve() throws IOException {
                HttpURLConnection c = (HttpURLConnection)metaNectarEndpoint.openConnection();
                c.setRequestMethod("HEAD");
                c.getResponseCode();
                String s = c.getHeaderField("X-MetaNectar-Port");
                if (s != null) {
                    int port = c.getHeaderFieldInt("X-MetaNectar-Port", 0);
                    c.getInputStream().close();
                    c.disconnect();
                    return new InetSocketAddress(metaNectarEndpoint.getHost(), port);
                } else {
                    throw new IOException("X-MetaNectar-Port header not present at " + metaNectarEndpoint.toExternalForm());
                }

            }
        }

        public TestMasterServer(URL metaNectarEndpoint, String organization, Map<String, String> properties) throws IOException {

            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", new HttpHandler() {
                public void handle(HttpExchange httpExchange) throws IOException {
                    Headers responseHeaders = httpExchange.getResponseHeaders();
                    responseHeaders.add("X-Jenkins", "");

                    responseHeaders.add("X-Instance-Identity", new String(Base64.encodeBase64(id.getPublic().getEncoded())));
                    httpExchange.sendResponseHeaders(200, 0);
                    httpExchange.close();
                }
            });
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            this.endpoint = new URL("http", "localhost", server.getAddress().getPort(), "");
            this.id = getId();
            this.metaNectarEndpoint = metaNectarEndpoint;
            this.organization = organization;

            MetaNectarAgentProtocol.Outbound p = new MetaNectarAgentProtocol.Outbound(
                    MetaNectarAgentProtocol.getInstanceIdentityCertificate(id, endpoint.toExternalForm()),
                    id.getPrivate(),
                    organization,
                    properties,
                    new Client(),
                    null);

            this.agent = new Agent(new AgentStatusListener.LoggerListener(LOGGER), new Resolver(), p);
        }


        // TODO copied from InstanceIdentity
        private InstanceIdentity getId() throws IOException {
            final KeyPair keys = generateKey();
            return new InstanceIdentity(File.createTempFile("key", null)) {
                public RSAPublicKey getPublic() {
                    return (RSAPublicKey)keys.getPublic();
                }

                public RSAPrivateKey getPrivate() {
                    return (RSAPrivateKey)keys.getPrivate();
                }
            };
        }

        // TODO copied from InstanceIdentity
        public KeyPair generateKey() {
            try {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048,new SecureRandom()); // going beyond 2048 requires crypto extension
                return gen.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError(e); // RSA algorithm should be always there
            }
        }

        public void setRetryInterval(int retryInterval) {
            agent.setRetryInterval(retryInterval);
        }

        public URL start() {
            new Thread(agent).start();
            return endpoint;
        }
    }
}
