package metanectar;

import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import metanectar.model.MasterServer;
import metanectar.test.MetaNectarTestCase;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.net.URL;
import java.util.Properties;
import java.util.UUID;

/**
 *
 * @author Paul Sandoz
 */
public class AboutDEVATcloudTest extends MetaNectarTestCase {

    private final static String PUBLIC_KEY = "THIS IS A KEY";

    public void testAbout() {
        AboutDEVATcloud a = metaNectar.getExtensionList(ManagementLink.class).get(AboutDEVATcloud.class);
        assertNotNull(a);
    }

    public void testWithPublicKey() throws Exception {
        File f = File.createTempFile("key", null);
        FileUtils.writeStringToFile(f, PUBLIC_KEY, "UTF-8");

        Properties p = new Properties();
        p.setProperty("metaNectar.master.ssh.username", "foo");
        p.setProperty("metaNectar.master.ssh.key.public", f.getAbsolutePath());
        metaNectar.setConfig(new Config(p));

        assertEquals(PUBLIC_KEY, getPublicKey());
    }

    public void testWithPublicKeyNonExistentFile() throws Exception {
        Properties p = new Properties();
        p.setProperty("metaNectar.master.ssh.username", "foo");
        p.setProperty("metaNectar.master.ssh.key.public", UUID.randomUUID().toString());
        metaNectar.setConfig(new Config(p));

        assertNull(getPublicKey());
    }

    public void testWithoutPublicKey() throws Exception {
        Properties p = new Properties();
        p.setProperty("metaNectar.master.ssh.username", "foo");
        metaNectar.setConfig(new Config(p));

        assertNull(getPublicKey());
    }

    private String getPublicKey() {
        return getAbout().getPublicKey();
    }
    private AboutDEVATcloud getAbout() {
        return metaNectar.getExtensionList(ManagementLink.class).get(AboutDEVATcloud.class);
    }
}
