package metanectar.provisioning;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.DumbSlave;
import metanectar.cloud.MasterProvisioningCloudProxy;
import metanectar.model.MasterServer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * @author Paul Sandoz
 */
public class MasterProvisioningLabelTest extends AbstractMasterProvisioningTestCase {

    public void testProvisionLocallyWithLabel() throws Exception {
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(1, new SimpleMasterProvisioningService(100)));

        MasterServer ms = createMaster("m1", "metamaster");

        ms.provisionAction().get(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.Provisioned, ms.getState());
        assertEquals(metaNectar, ms.getNode());
    }

    public void testProvisionLocallyWithUnknownLabel() throws Exception {
        metaNectar.getNodeProperties().add(new MasterProvisioningNodeProperty(1, new SimpleMasterProvisioningService(100)));

        MasterServer ms = createMaster("m1", "unknonwn");

        LatchMasterServerListener noResources = new LatchMasterServerListener(1) {
            public void onProvisioningErrorNoResources(MasterServer ms) {
                countDown();
            }
        };

        ms.provisionAction();

        noResources.await(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.ProvisioningErrorNoResources, ms.getState());
    }


    public void testProvisionWithSlaves() throws Exception {
        Node l1 = createProvisioningSlave("l1");
        Node l2 = createProvisioningSlave("l2");

        MasterServer m1 = createMaster("m1", "l1");
        MasterServer m2 = createMaster("m2", "l2");

        m1.provisionAction().get(1, TimeUnit.MINUTES);
        m2.provisionAction().get(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.Provisioned, m1.getState());
        assertEquals(MasterServer.State.Provisioned, m2.getState());

        assertEquals(l1, m1.getNode());
        assertEquals(l2, m2.getNode());
    }

    public void testProvisionSlaveWithUnknownLabel() throws Exception {
        Node l1 = createProvisioningSlave("l1");

        MasterServer ms = createMaster("m1", "foo");

        LatchMasterServerListener noResources = new LatchMasterServerListener(1) {
            public void onProvisioningErrorNoResources(MasterServer ms) {
                countDown();
            }
        };

        ms.provisionAction();

        noResources.await(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.ProvisioningErrorNoResources, ms.getState());
    }

    public void testProvisionWithClouds() throws Exception {
        int clouds = 4;
        int maxMastersPerNode = 10;

        List<MasterServer> mss = Lists.newArrayList();
        for (int i = 0; i < clouds; i++) {
            String label = "c" + i;
            createProvisioningCloud(maxMastersPerNode, label);

            for (int j = 0; j < maxMastersPerNode; j++)  {
                mss.add(createMaster("m" + j + label, label));
            }
        }

        Collections.shuffle(mss);

        for (MasterServer ms : mss) {
            ms.provisionAction().get(1, TimeUnit.MINUTES);
        }

        for (MasterServer ms : mss) {
            assertEquals(MasterServer.State.Provisioned, ms.getState());

            Label l = ms.getLabel();
            assertTrue(l.contains(ms.getNode()));
        }

        assertEquals(4, metaNectar.getNodes().size());

        for (Node n : metaNectar.getNodes()) {
            List<MasterServer> mssn = metaNectar.masterProvisioner.getProvisionedMasters().get(n);

            assertEquals(maxMastersPerNode, mssn.size());

            Set<String> labels = Sets.newHashSet();
            for (MasterServer ms : mssn) {
                labels.add(ms.getLabelExpression());
            }
            assertEquals(1, labels.size());
        }
    }

    public void testProvisionCloudWithUnknownLabel() throws Exception {
        createProvisioningCloud(1, "c1");

        MasterServer ms = createMaster("m1", "foo");

        LatchMasterServerListener noResources = new LatchMasterServerListener(1) {
            public void onProvisioningErrorNoResources(MasterServer ms) {
                countDown();
            }
        };

        ms.provisionAction();

        noResources.await(1, TimeUnit.MINUTES);

        assertEquals(MasterServer.State.ProvisioningErrorNoResources, ms.getState());
    }

    private MasterServer createMaster(String name, String label) throws IOException {
        MasterServer ms = metaNectar.createManagedMaster(name);
        ms.setLabelExpression(label);
        return ms;
    }

    private DumbSlave createProvisioningSlave(String label) throws Exception {
        DumbSlave slave = createSlave(label, null);
        slave.getNodeProperties().add(new MasterProvisioningNodeProperty(1, new SimpleMasterProvisioningService(100)));
        Computer computer = slave.toComputer();
        computer.connect(false).get();
        return slave;
    }

    private Cloud createProvisioningCloud(int maxMasters, String label) throws Exception {
        MasterProvisioningNodePropertyTemplate tp = new MasterProvisioningNodePropertyTemplate(maxMasters, new SimpleMasterProvisioningService(100));
        MasterProvisioningCloudProxy pc = new MasterProvisioningCloudProxy(tp, new DummySlaveCloud(this, 100, label));
        metaNectar.clouds.add(pc);
        return pc;
    }
}
