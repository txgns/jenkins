package metanectar.model;

import metanectar.test.MetaNectarTestCase;
import sun.security.rsa.RSAKeyPairGenerator;

import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

/**
 * @author Kohsuke Kawaguchi
 */
public class MasterServerTest extends MetaNectarTestCase {
    /**
     * Makes sure that the key round-trips in {@link MasterServer#getIdentityPublicKey()}.
     */
    public void testAcknowledgement() throws Exception {
        final MasterServer s = metaNectar.createManagedMaster("org");

        RSAKeyPairGenerator gen = new RSAKeyPairGenerator();
        gen.initialize(2048,new SecureRandom());
        KeyPair userKey = gen.generateKeyPair();
        PublicKey original = userKey.getPublic();

        s.setApprovedState((RSAPublicKey) original, getURL());
        final RSAPublicKey current = s.getIdentityPublicKey();

        assertTrue(Arrays.equals(original.getEncoded(), current.getEncoded()));
    }
}
