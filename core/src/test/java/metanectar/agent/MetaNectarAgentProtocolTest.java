package metanectar.agent;

import hudson.remoting.Channel;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import hudson.util.IOException2;
import junit.framework.TestCase;
import metanectar.agent.MetaNectarAgentProtocol.GracefulConnectionRefusalException;
import metanectar.agent.MetaNectarAgentProtocol.Listener;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateIssuerName;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateSubjectName;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Kohsuke Kawaguchi
 */
public class MetaNectarAgentProtocolTest extends TestCase {
    private Entity server;
    private Entity client;
    private ExecutorService es;
    private Future<?> serverOutcome;
    private Future<?> clientOutcome;

    /**
     * Entity in the RSA sense.
     */
    class Entity {
        public final X509Certificate cert;
        public final RSAPrivateKey privateKey;

        /**
         * Generates a key pair and corresponding X509 certificate.
         */
        Entity(String name) throws GeneralSecurityException, IOException {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048,new SecureRandom()); // going beyond 2048 requires crypto extension
            KeyPair keys = gen.generateKeyPair();

            privateKey = (RSAPrivateKey)keys.getPrivate();

            Date firstDate = new Date();
            Date lastDate = new Date(firstDate.getTime()+ TimeUnit.DAYS.toMillis(365));

            CertificateValidity interval = new CertificateValidity(firstDate, lastDate);

            X500Name subject = new X500Name(name, "", "", "US");
            X509CertInfo info = new X509CertInfo();
            info.set(X509CertInfo.VERSION,new CertificateVersion(CertificateVersion.V3));
            info.set(X509CertInfo.SERIAL_NUMBER,new CertificateSerialNumber(1));
            info.set(X509CertInfo.ALGORITHM_ID,new CertificateAlgorithmId(AlgorithmId.get("SHA1WithRSA")));
            info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(subject));
            info.set(X509CertInfo.KEY, new CertificateX509Key(keys.getPublic()));
            info.set(X509CertInfo.VALIDITY, interval);
            info.set(X509CertInfo.ISSUER,   new CertificateIssuerName(subject));

            // sign it
            X509CertImpl cert = new X509CertImpl(info);
            cert.sign(privateKey, "SHA1withRSA");

            this.cert = cert;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new Entity("server");
        client = new Entity("client");
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

        serverOutcome = es.submit(new Callable<Object>() {
            public Object call() throws Exception {
                MetaNectarAgentProtocol s = new MetaNectarAgentProtocol.Inbound(server.cert, server.privateKey, sl);
                s.process(new Connection(i1, new FastPipedOutputStream(i2)));
                return null;
            }
        });

        clientOutcome = es.submit(new Callable<Object>() {
            public Object call() throws Exception {
                MetaNectarAgentProtocol s = new MetaNectarAgentProtocol.Outbound(client.cert, client.privateKey, cl);
                s.process(new Connection(i2, new FastPipedOutputStream(i1)));
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
