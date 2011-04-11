package metanectar.provisioning.test;

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
import hudson.remoting.VirtualChannel;
import metanectar.provisioning.Master;
import metanectar.provisioning.MasterProvisioningService;
import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A test master provisioning service that creates a fake master.
 *
 * @author Paul Sandoz
*/
@Extension
public class TestMasterProvisioningService extends MasterProvisioningService {

    private final int delay;

    @DataBoundConstructor
    public TestMasterProvisioningService() {
        this(1000);
    }

    public TestMasterProvisioningService(int delay) {
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
        return null;
    }

    public Map<String, Master> getProvisioned(VirtualChannel channel) {
        return null;
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

    @Extension
    public static class DescriptorImpl extends Descriptor<MasterProvisioningService> {
        public String getDisplayName() {
            return "Test Master Provisioning Service";
        }
    }

    static class TestMasterServer {
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
                    OutputStream out = httpExchange.getResponseBody();
                    out.write("This is a fake master".getBytes());
                    out.close();
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
