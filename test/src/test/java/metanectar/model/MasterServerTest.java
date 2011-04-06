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
     * Makes sure that the key round-trips in {@link MasterServer#getIdentity()}.
     */
    public void testAcknowledgement() throws Exception {
        final boolean old = MetaNectar.BYPASS_INSTANCE_AUTHENTICATION;
        MetaNectar.BYPASS_INSTANCE_AUTHENTICATION=true;

        try {
            final MasterServer s = metaNectar.doAddMasterServer(getURL());

            RSAKeyPairGenerator gen = new RSAKeyPairGenerator();
            gen.initialize(2048,new SecureRandom());
            KeyPair userKey = gen.generateKeyPair();
            PublicKey original = userKey.getPublic();

            s.setIdentity((RSAPublicKey) original);
            final RSAPublicKey current = s.getIdentity();

            assertTrue(Arrays.equals(original.getEncoded(), current.getEncoded()));
        } finally {
            MetaNectar.BYPASS_INSTANCE_AUTHENTICATION=old;
        }
    }
}
