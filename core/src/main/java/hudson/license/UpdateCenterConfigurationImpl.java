package hudson.license;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Hudson;
import hudson.model.UpdateCenter.DownloadJob;
import hudson.model.UpdateCenter.UpdateCenterConfiguration;
import hudson.util.IOException2;
import org.jvnet.hudson.crypto.CertificateUtil;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;

import static java.util.Arrays.*;
import static java.util.Collections.*;

/**
 * {@link UpdateCenterConfiguration} 
 *
 * @author Kohsuke Kawaguchi
 */
public class UpdateCenterConfigurationImpl extends UpdateCenterConfiguration {
    @Override
    protected URLConnection connect(DownloadJob job, URL src) throws IOException {
        URLConnection con = super.connect(job, src);
        if (con instanceof HttpsURLConnection) {
            try {
                HttpsURLConnection scon = (HttpsURLConnection) con;
                scon.setSSLSocketFactory(createFactory());
            } catch (GeneralSecurityException e) {
                throw new IOException2("Failed to prepare for the SSL client authentication",e);
            }
        }
        return con;
    }

    protected SSLSocketFactory createFactory() throws GeneralSecurityException, IOException {
        License lic = Hudson.getInstance().getLicense().getParsed();

        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(null);
        keystore.setKeyEntry("nectar", lic.getPrivateKey(), KEYSTORE_PASSWORD, new Certificate[]{lic.getCert()});

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keystore, KEYSTORE_PASSWORD);

        final X509Certificate ca = License.loadLicenseCaCertificate();

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagerFactory.getKeyManagers(), new TrustManager[]{
                /**
                 * Only allow client certificates signed by our CA.
                 */
                new X509TrustManager() {
                    X509TrustManager defaultImpl = CertificateUtil.getDefaultX509TrustManager();

                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        try {
                            CertificateUtil.validatePath(asList(chain), singleton(new TrustAnchor(ca, null)));
                        } catch (GeneralSecurityException e) {
                            throw new CertificateException("Failed to validate the cert", e);
                        }
                    }

                    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        defaultImpl.checkServerTrusted(chain,authType);
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{ca};
                    }
                }

        }, null);

        return context.getSocketFactory();
    }

    @Initializer(after=InitMilestone.JOB_LOADED)
    public static void install() {
        Hudson.getInstance().getUpdateCenter().configure(new UpdateCenterConfigurationImpl());
    }

    private static final char[] KEYSTORE_PASSWORD = "unused".toCharArray();
}
