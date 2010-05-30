package hudson.license;

import com.trilead.ssh2.crypto.PEMDecoder;
import com.trilead.ssh2.signature.RSAPrivateKey;
import hudson.util.FormValidation;
import org.jvnet.hudson.crypto.CertificateUtil;
import sun.security.x509.X500Name;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * License key.
 *
 * @author Kohsuke Kawaguchi
 */
public final class License {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final X509Certificate cert;
    private final X500Name name;

    // information parsed from the name
    private int executors;
    private String serverKey;

    License(String key, String certificate) throws GeneralSecurityException, IOException {
        key = key.trim();
        certificate = certificate.trim();

        try {
            RSAPrivateKey k = (RSAPrivateKey) PEMDecoder.decode(key.toCharArray(), null);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.publicKey = kf.generatePublic(new RSAPublicKeySpec(k.getN(), k.getE()));
            this.privateKey = kf.generatePrivate(new RSAPrivateKeySpec(k.getN(), k.getD()));
        } catch (IOException e) {
            throw FormValidation.error(e,"Invalid license key");
        } catch (GeneralSecurityException e) {
            throw FormValidation.error(e,"Invalid license key");
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try {
            this.cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificate.getBytes()));
        } catch (CertificateException e) {
            throw FormValidation.error(e,"Invalid certificate");
        }

        if (!cert.getPublicKey().equals(publicKey))
            throw FormValidation.error("Key and certificate doesn't match");

        name = new X500Name(cert.getSubjectX500Principal().getName());

        // check that the current timestamp is within the subscription period
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException e) {
            throw FormValidation.error(e,"Subscription has expired");
        } catch (CertificateNotYetValidException e) {
            throw FormValidation.error(e,"Subscription is not yet active");
        }

        {// parse the limits
            String o = name.getOrganization();
            if (!o.startsWith(HEADER))
                throw FormValidation.error("Invalid organization name");
            o = o.substring(HEADER.length());
            for (String token : o.split(",")) {
                int pos = token.indexOf('=');
                String n = token.substring(0, pos);
                String v = token.substring(pos+1,token.length());
                if (n.equals("executors"))
                    executors = Integer.valueOf(v);
                if (n.equals("serverKey"))
                    serverKey = v;
            }
        }

        if (!LicenseManager.getServerKey().equals(serverKey))
            throw FormValidation.error("This license belongs to another server: "+serverKey);

        // make sure that it's got the valid trust chain
        Set<TrustAnchor> anchors = new HashSet<TrustAnchor>();
        X509Certificate ca = (X509Certificate) cf.generateCertificate(getClass().getResourceAsStream("/license-root"));
        anchors.add(new TrustAnchor(ca,null));

        try {
            CertificateUtil.validatePath(Arrays.asList(cert,ca), anchors);
        } catch (GeneralSecurityException e) {
            throw FormValidation.error(e,"Invalid CA in the license key");
        }
    }

    /**
     * This license is valid until...
     */
    public Date getExpirationDate() {
        return cert.getNotAfter();
    }

    public String getCustomerName() throws IOException {
        return name.getCommonName();
    }

    public int getExecutorsLimit() {
        return executors;
    }

    private static final String HEADER = "Hudson Customer:";
}
