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
public class JenkinsServerTest extends MetaNectarTestCase {
    /**
     * Makes sure that the key round-trips in {@link JenkinsServer#getAcknowledgedKey()}.
     */
    public void testAcknowledgement() throws Exception {
        final boolean old = MetaNectar.BYPASS_INSTANCE_AUTHENTICATION;
        MetaNectar.BYPASS_INSTANCE_AUTHENTICATION=true;

        try {
            final JenkinsServer s = metaNectar.doAddNectar(getURL());

            RSAKeyPairGenerator gen = new RSAKeyPairGenerator();
            gen.initialize(2048,new SecureRandom());
            KeyPair userKey = gen.generateKeyPair();
            PublicKey original = userKey.getPublic();

            s.setAcknowledgedKey((RSAPublicKey) original);
            final RSAPublicKey current = s.getAcknowledgedKey();

            assertTrue(Arrays.equals(original.getEncoded(), current.getEncoded()));
        } finally {
            MetaNectar.BYPASS_INSTANCE_AUTHENTICATION=old;
        }
    }
}
