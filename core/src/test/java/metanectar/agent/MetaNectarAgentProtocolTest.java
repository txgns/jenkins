package metanectar.agent;

import hudson.remoting.Channel;
import hudson.remoting.Channel.Listener;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import hudson.util.IOException2;
import junit.framework.TestCase;
import sun.security.x509.AlgorithmId;
import sun.security.x509.BasicConstraintsExtension;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateExtensions;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Kohsuke Kawaguchi
 */
public class MetaNectarAgentProtocolTest extends TestCase {
    private Entity server;
    private Entity client;

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
    }

    /**
     * Runs the protocol between two listeners concurrently and return when done.
     */
    public void runProtocol(final MetaNectarAgentProtocol.Listener sl, final MetaNectarAgentProtocol.Listener cl) throws Exception {
        final FastPipedInputStream i1 = new FastPipedInputStream();
        final FastPipedInputStream i2 = new FastPipedInputStream();

        ExecutorService es = Executors.newCachedThreadPool();
        Future<?> f1 = es.submit(new Callable<Object>() {
            public Object call() throws Exception {
                MetaNectarAgentProtocol s = new MetaNectarAgentProtocol.Inbound(server.cert, server.privateKey, new URL("http://server/"), sl);
                s.process(new Connection(i1, new FastPipedOutputStream(i2)));
                return null;
            }
        });

        Future<?> f2 = es.submit(new Callable<Object>() {
            public Object call() throws Exception {
                MetaNectarAgentProtocol s = new MetaNectarAgentProtocol.Outbound(client.cert, client.privateKey, new URL("http://client/"), cl);
                s.process(new Connection(i2, new FastPipedOutputStream(i1)));
                return null;
            }
        });

        f1.get();
        f2.get();
        es.shutdown();
    }

    public void testBasics() throws Exception {
        runProtocol(new metanectar.agent.MetaNectarAgentProtocol.Listener() {
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
        }, new metanectar.agent.MetaNectarAgentProtocol.Listener() {
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
}
