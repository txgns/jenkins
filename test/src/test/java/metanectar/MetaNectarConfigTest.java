package metanectar;

import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import metanectar.provisioning.MasterProvisioningNodeProperty;
import metanectar.test.MetaNectarTestCase;

import java.util.Properties;

/**
 * @author Paul Sandoz
 */
public class MetaNectarConfigTest extends MetaNectarTestCase {

    private Properties getDefaultProperties(boolean isMasterProvisioning) {
        Properties properties = new Properties();
        properties.put("metaNectar.endpoint", "http://localhost:8080");
        properties.put("metaNectar.isMasterProvisioning", Boolean.toString(isMasterProvisioning));
        properties.put("metaNectar.master.provisioning.basePort", "8181");
        properties.put("metaNectar.master.provisioning.homeLocation", "/tmp/masters");
        properties.put("metaNectar.master.provisioning.timeOut", "10");
        properties.put("metaNectar.master.provisioning.script.provision", "provision");
        properties.put("metaNectar.master.provisioning.script.start", "start");
        properties.put("metaNectar.master.provisioning.script.stop", "stop");
        properties.put("metaNectar.master.provisioning.script.terminate", "terminate");
        return properties;
    }

    public void testMasterProvisioning() {
        metaNectar.setConfig(new Config(getDefaultProperties(true)));

        NodePropertyDescriptor d = NodeProperty.all().find(MasterProvisioningNodeProperty.class);
        assertNotNull(d.getDisplayName());
    }

    public void testNoMasterProvisioning() {
        metaNectar.setConfig(new Config(getDefaultProperties(false)));

        NodePropertyDescriptor d = NodeProperty.all().find(MasterProvisioningNodeProperty.class);
        assertNull(d.getDisplayName());
    }
}