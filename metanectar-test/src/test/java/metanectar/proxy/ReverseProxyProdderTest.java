package metanectar.proxy;

import metanectar.Config;
import metanectar.model.MasterServer;
import metanectar.model.MasterServerListener;
import metanectar.provisioning.AbstractMasterProvisioningTestCase;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

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

    private ReverseProxyProdder initiate() throws Exception {
        ReverseProxyProdder rpp = MasterServerListener.all().get(ReverseProxyProdder.class);
        rpp.await(1, TimeUnit.MINUTES);
        assertEquals(1, rpp.getActualProdCount());
        assertFalse(f.exists());

        return rpp;
    }
    private ReverseProxyProdder initiateAndReset() throws Exception {
        ReverseProxyProdder rpp = initiate();

        f.createNewFile();
        assertTrue(f.exists());
        return rpp;
    }

    public void testInitiate() throws Exception {
        initiate();
    }

    public void testProvision() throws Exception {
        configureSimpleMasterProvisioningOnMetaNectar();
        ReverseProxyProdder rpp = initiateAndReset();

        provisionAndStartMaster("o1");
        rpp.await(1, TimeUnit.MINUTES);

        assertFalse(f.exists());
    }

    public void testTerminate() throws Exception {
        configureSimpleMasterProvisioningOnMetaNectar();
        ReverseProxyProdder rpp = initiateAndReset();

        MasterServer o1 = provisionAndStartMaster("o1");
        rpp.await(1, TimeUnit.MINUTES);
        assertFalse(f.exists());

        f.createNewFile();
        assertTrue(f.exists());

        terminateAndDeleteMaster(o1);
        rpp.await(1, TimeUnit.MINUTES);
        assertFalse(f.exists());
    }

    public void testMultiple() throws Exception {
        int n = 100;
        configureSimpleMasterProvisioningOnMetaNectar(n);
        ReverseProxyProdder rpp = initiateAndReset();

        List<MasterServer> l = provisionAndStartMasters("o", n);
        rpp.await(1, TimeUnit.MINUTES);

        for (MasterServer ms : l) {
            assertEquals(MasterServer.State.Started, ms.getState());
        }

        int i = rpp.getActualProdCount();
        assertTrue(i < rpp.getRequestedProdCount());
        assertEquals(n * 6 + 1, rpp.getRequestedProdCount());

        terminateAndDeleteMasters(l);
        rpp.await(1, TimeUnit.MINUTES);

        assertTrue(i < rpp.getActualProdCount());
        assertTrue(rpp.getActualProdCount() < rpp.getRequestedProdCount());
        assertEquals(n * 6 + n * 4 + 1, rpp.getRequestedProdCount());
    }

}
