package hudson.license;

import hudson.util.IOUtils;
import hudson.util.NullStream;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * @author Kohsuke Kawaguchi
 */
public class UpdateCenterConfigurationImplTest extends HudsonTestCase {
    public void testConnection() throws Exception {
        try {
            URLConnection con = new UpdateCenterConfigurationImpl().connect(null, new URL("https://nectar-updates.cloudbees.com/updateCenter/1.383/plugins/bogus/1.0/bogus-1.0.hpi"));
            IOUtils.copy(con.getInputStream(),new NullStream());
            fail();
        } catch (FileNotFoundException e) {
            // as expected, as this file doesn't exist
        }
        // we are making sure that there's no PKIX path building failure here
    }
}
