package metanectar.agent;

import hudson.remoting.Channel;
import metanectar.agent.Agent.AgentException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.DHParameterSpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.security.AlgorithmParameterGenerator;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;

/**
 * The Jenkins/Nectar protocol that establishes the channel with MetaNectar.
 *
 * @author Paul Sandoz
 * @author Kohsuke Kawaguchi
 */
public class MetaNectarAgentProtocolOutbound implements AgentProtocol.Outbound {
    private final X509Certificate identity;
    private final IdentityChecker checker;

    public interface IdentityChecker {
        void onConnectingTo(X509Certificate serverIdentity) throws GeneralSecurityException;
    }


    public MetaNectarAgentProtocolOutbound(X509Certificate identity, IdentityChecker checker) {
        this.identity = identity;
        this.checker = checker;
    }

    public String getName() {
        return "Protocol:MetaNectar";
    }

    public void process(Connection connection) throws Exception {
        AlgorithmParameterGenerator paramGen = AlgorithmParameterGenerator.getInstance("DH");
        paramGen.init(512);

        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("DH");
        keyPairGen.initialize(paramGen.generateParameters().getParameterSpec(DHParameterSpec.class));
        KeyPair keyPair = keyPairGen.generateKeyPair();

        KeyAgreement keyAgree = KeyAgreement.getInstance("DH");
        keyAgree.init(keyPair.getPrivate());

        Map<String,Object> requestHeaders = new HashMap<String,Object>();
        requestHeaders.put("Diffie-Hellman",keyPair.getPublic().getEncoded());
        requestHeaders.put("Identity", identity);

        connection.sendObject(requestHeaders);
        Map<String,Object> responseHeaders = connection.readObject();

        byte[] otherHalf = (byte[])responseHeaders.get("Diffie-Hellman");
        X509Certificate server = (X509Certificate)responseHeaders.get("Identity");
        if (server==null)
            throw new IOException("The server failed to give us its identity");

        checker.onConnectingTo(server);

        PublicKey destinationPubKey = KeyFactory.getInstance("DH").generatePublic(new X509EncodedKeySpec(otherHalf));
        keyAgree.doPhase(destinationPubKey, true);
        SecretKey secret = keyAgree.generateSecret("DES");

        Cipher cipher = Cipher.getInstance("DES");
        cipher.init(0,secret);

        final Channel channel = new Channel("outbound-channel", executor,
                new BufferedInputStream(new CipherInputStream(connection.in,)),
                new BufferedOutputStream(con.out));

        new CipherOutputStream()

        // Get the meta nectar root action instance
        // set the channel on that instance
    }
}
