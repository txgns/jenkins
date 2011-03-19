package metanectar.agent;

import hudson.remoting.Channel;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import hudson.util.IOException2;
import junit.framework.TestCase;
import metanectar.agent.MetaNectarAgentProtocol.GracefulConnectionRefusalException;
import metanectar.agent.MetaNectarAgentProtocol.Listener;

import java.io.IOException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Kohsuke Kawaguchi
 */
public class MetaNectarAgentProtocolTest extends TestCase {
    private CryptographicEntity server;
    private CryptographicEntity client;
    private ExecutorService es;
    private Future<?> serverOutcome;
    private Future<?> clientOutcome;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new CryptographicEntity("server");
        client = new CryptographicEntity("client");
        es = Executors.newCachedThreadPool();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        es.shutdown();

        if (serverOutcome!=null)
            serverOutcome.get();
        if (clientOutcome!=null)
            clientOutcome.get();
    }

    /**
     * Runs the protocol between two listeners concurrently and return when done.
     */
    public void runProtocol(final MetaNectarAgentProtocol.Listener sl, final MetaNectarAgentProtocol.Listener cl) throws Exception {
        final FastPipedInputStream i1 = new FastPipedInputStream();
        final FastPipedInputStream i2 = new FastPipedInputStream();
        final FastPipedOutputStream o1 = new FastPipedOutputStream(i2);
        final FastPipedOutputStream o2 = new FastPipedOutputStream(i1);

        serverOutcome = es.submit(new Callable<Object>() {
            public Object call() throws Exception {
                MetaNectarAgentProtocol s = new MetaNectarAgentProtocol.Inbound(server.cert, server.privateKey, sl);
                s.process(new Connection(i1, o1));
                return null;
            }
        });

        clientOutcome = es.submit(new Callable<Object>() {
            public Object call() throws Exception {
                MetaNectarAgentProtocol s = new MetaNectarAgentProtocol.Outbound(client.cert, client.privateKey, cl);
                s.process(new Connection(i2, o2));
                return null;
            }
        });
    }

    public void evalClient() throws Exception {
        try {
            clientOutcome.get();
        } catch (ExecutionException e) {
            throw (Exception)e.getCause();
        } finally {
            clientOutcome = null;
        }
    }

    public void evalServer() throws Exception {
        try {
            serverOutcome.get();
        } catch (ExecutionException e) {
            throw (Exception)e.getCause();
        } finally {
            serverOutcome = null;
        }
    }

    /**
     * Success scenario.
     */
    public void testSuccess() throws Exception {
        runProtocol(new AbstractListenerImpl() {
            public void onConnectingTo(URL address, X509Certificate identity) {
                assertEquals(identity, client.cert);
            }

            public void onConnectedTo(Channel channel, X509Certificate identity) throws IOException {
                try {
                    channel.setProperty("hello","from server");
                    assertEquals("from client",(String)channel.waitForRemoteProperty("hello"));
                    channel.close();
                } catch (InterruptedException e) {
                    throw new IOException2(e);
                }
            }
        }, new AbstractListenerImpl() {
            public void onConnectingTo(URL address, X509Certificate identity) {
                assertEquals(identity, server.cert);
            }

            public void onConnectedTo(Channel channel, X509Certificate identity) throws IOException {
                try {
                    channel.setProperty("hello","from client");
                    assertEquals("from server", (String) channel.waitForRemoteProperty("hello"));
                    channel.close();
                } catch (InterruptedException e) {
                    throw new IOException2(e);
                }
            }
        });
    }

    /**
     * Tests a rejection.
     */
    public void testRejection() throws Exception {
        runProtocol(new AbstractListenerImpl() {
            public void onConnectingTo(URL address, X509Certificate identity) throws GracefulConnectionRefusalException {
                throw new GracefulConnectionRefusalException("I don't like you");
            }

            public void onConnectedTo(Channel channel, X509Certificate identity) throws IOException {
                fail();
            }
        }, new AbstractListenerImpl() {
            public void onConnectingTo(URL address, X509Certificate identity) {
                // accept the server
            }

            public void onConnectedTo(Channel channel, X509Certificate identity) throws IOException {
                fail();
            }
        });

        try {
            evalClient();
            fail();
        } catch (GracefulConnectionRefusalException e) {
            assertEquals("I don't like you",e.getMessage());
        }

        try {
            evalServer();
            fail();
        } catch (GracefulConnectionRefusalException e) {
            assertEquals("I don't like you",e.getMessage());
        }
    }

    protected abstract class AbstractListenerImpl extends Listener {
        public URL getOurURL() throws IOException {
            return new URL("http://somewhere/");
        }
    }
}
