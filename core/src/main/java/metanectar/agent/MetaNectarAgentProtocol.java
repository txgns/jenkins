package metanectar.agent;

import hudson.model.Hudson.MasterComputer;
import hudson.model.UsageStatistics.CombinedCipherInputStream;
import hudson.model.UsageStatistics.CombinedCipherOutputStream;
import hudson.remoting.Channel;
import metanectar.agent.AgentProtocol.Inbound;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;

/**
 * The Jenkins/Nectar protocol that establishes the channel with MetaNectar.
 *
 * @author Paul Sandoz
 * @author Kohsuke Kawaguchi
 */
public abstract class MetaNectarAgentProtocol implements AgentProtocol {
    /**
     * Certificate that shows the identity of this client.
     */
    private final X509Certificate identity;
    /**
     * Private key that pairs up with the {@link #identity}
     */
    private final RSAPrivateKey privateKey;

    private final Listener listener;

    public interface Listener {
        void onConnectingTo(X509Certificate serverIdentity) throws GeneralSecurityException;
        void onConnectedTo(Channel channel);
    }

    public MetaNectarAgentProtocol(X509Certificate identity, RSAPrivateKey privateKey, Listener listener) {
        this.identity = identity;
        this.privateKey = privateKey;
        this.listener = listener;
    }

    public String getName() {
        return "Protocol:MetaNectar";
    }

    public static class Inbound extends MetaNectarAgentProtocol implements AgentProtocol.Inbound {
        public Inbound(X509Certificate identity, RSAPrivateKey privateKey, Listener listener) {
            super(identity, privateKey, listener);
        }

        public void process(Connection connection) throws Exception {
            Map<String,Object> responseHeaders = connection.readObject();
            sendHandshake(connection);
            connect(connection, responseHeaders);
        }
    }

    public static class Outbound extends MetaNectarAgentProtocol implements AgentProtocol.Outbound {
        public Outbound(X509Certificate identity, RSAPrivateKey privateKey, Listener listener) {
            super(identity, privateKey, listener);
        }

        public void process(Connection connection) throws Exception {
            sendHandshake(connection);
            Map<String,Object> responseHeaders = connection.readObject();
            connect(connection, responseHeaders);
        }
    }


    protected void connect(Connection connection, Map<String, Object> responseHeaders) throws IOException, GeneralSecurityException {
        X509Certificate server = (X509Certificate)responseHeaders.get("Identity");
        if (server==null)
            throw new IOException("The server failed to give us its identity");

        listener.onConnectingTo(server);

        final Channel channel = new Channel("outbound-channel", MasterComputer.threadPoolForRemoting,
            new BufferedInputStream(new CombinedCipherInputStream(connection.in, (RSAPublicKey)server.getPublicKey(), "AES")),
            new BufferedOutputStream(new CombinedCipherOutputStream(connection.out, privateKey, "AES")));

        listener.onConnectedTo(channel);
    }

    protected void sendHandshake(Connection connection) throws IOException {
        // use a map in preparation for possible future extension
        Map<String,Object> requestHeaders = new HashMap<String,Object>();
        requestHeaders.put("Identity", identity);

        connection.sendObject(requestHeaders);
    }
}
