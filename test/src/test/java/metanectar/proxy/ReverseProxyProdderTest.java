package metanectar.proxy;

import metanectar.Config;
import metanectar.model.MasterServer;
import metanectar.model.MasterServerListener;
import metanectar.provisioning.AbstractMasterProvisioningTestCase;

import java.io.File;
import java.util.List;
import java.util.Properties;

/**
 * This test will only work on UNIX.
 *
 * @author Paul Sandoz
 */
public class ReverseProxyProdderTest extends AbstractMasterProvisioningTestCase {

    private File f;

    @Override
    protected void setUp() throws Exception {
        Properties p = new Properties();
        f = File.createTempFile("pre", "post");
        p.setProperty("metaNectar.proxy.script.reload", "rm " + f.getAbsolutePath());
        setConfig(new Config(p));
        super.setUp();
    }

    public void testProvision() throws Exception {
        configureDummyMasterProvisioningOnMetaNectar();

        provisionAndStartMaster("o1");

        assertFalse(f.exists());
    }

    public void testTerminate() throws Exception {
        configureDummyMasterProvisioningOnMetaNectar();

        MasterServer o1 = provisionAndStartMaster("o1");
        assertFalse(f.exists());

        f.createNewFile();
        assertTrue(f.exists());

        terminateAndDeleteMaster(o1);
        assertFalse(f.exists());
    }

    public void testMultiple() throws Exception {
        configureDummyMasterProvisioningOnMetaNectar(100);

        ReverseProxyProdder rpp = MasterServerListener.all().get(ReverseProxyProdder.class);

        List<MasterServer> l = provisionAndStartMasters("o", 100);

        int i = rpp.getActualProdCount();
        assertTrue(i < rpp.getRequestedProdCount());
        assertEquals(100, rpp.getRequestedProdCount());

        terminateAndDeleteMasters(l);

        assertTrue(i < rpp.getActualProdCount());
        assertTrue(rpp.getActualProdCount() < rpp.getRequestedProdCount());
        assertEquals(200, rpp.getRequestedProdCount());
    }

}
