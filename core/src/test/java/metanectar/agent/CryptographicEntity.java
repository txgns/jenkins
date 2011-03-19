package metanectar.agent;

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
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Entity in the RSA sense.
 */
public class CryptographicEntity {
    public final X509Certificate cert;
    public final RSAPrivateKey privateKey;

    /**
     * Generates a key pair and corresponding X509 certificate.
     */
    public CryptographicEntity(String name) throws GeneralSecurityException, IOException {
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
