package metanectar;

import com.google.common.collect.Maps;
import junit.framework.TestCase;

import java.net.URL;
import java.util.Map;

/**
 * @author Paul Sandoz
 */
public class ConfigTest extends TestCase {

    private Map<String, String> getDefaultProperties() {
        Map<String, String> properties = Maps.newHashMap();
        properties.put("metaNectar.endpoint", "http://localhost:8080");
        properties.put("metaNectar.isMasterProvisioning", "true");
        properties.put("metaNectar.master.provisioning.basePort", "8181");
        properties.put("metaNectar.master.provisioning.homeLocation", "/tmp/masters");
        properties.put("metaNectar.master.provisioning.timeOut", "10");
        properties.put("metaNectar.master.provisioning.script.provision", "provision");
        properties.put("metaNectar.master.provisioning.script.start", "start");
        properties.put("metaNectar.master.provisioning.script.stop", "stop");
        properties.put("metaNectar.master.provisioning.script.terminate", "terminate");
        return properties;
    }

    public void testValidProperties() throws Exception {
        try {
            System.setProperty(Config.METANECTAR_PROPERTIES_URL_SYSTEM_PROPERTY_NAME,
                    ConfigTest.class.getResource("config.properties").toExternalForm());

            Config c = Config.getInstance();
            testProperties(c);
        } finally {
            System.clearProperty(Config.METANECTAR_PROPERTIES_URL_SYSTEM_PROPERTY_NAME);
        }
    }

    public void testValidPropertiesUsingSystemProperties() throws Exception {
        Map<String, String> properties = getDefaultProperties();
        try {
            for (Map.Entry<String, String> e : properties.entrySet()) {
                System.setProperty(e.getKey(), e.getValue());
            }
            final Config c = new Config((String)null);
            testProperties(c);
        } finally {
            for (String name : properties.keySet()) {
                System.clearProperty(name);
            }
        }
    }

    public void testBadConfigUrl() throws Exception {
        final Config c = new Config("badUrl");
        testNoProperties(c);
    }

    public void testNoProperties() throws Exception {
        final Config c = new Config((String)null);
        testNoProperties(c);
    }

    public void testNoPropertiesInPropertiesFile() throws Exception {
        final Config c = new Config(ConfigTest.class.getResource("noconfig.properties").toExternalForm());
        testNoProperties(c);
    }

    public void testNoValuePropertiesInPropertiesFile() throws Exception {
        final Config c = new Config(ConfigTest.class.getResource("novalue.config.properties").toExternalForm());
        testNoProperties(c);
    }

    private void testProperties(final Config c) throws Exception{
        assertEquals(new URL("http://localhost:8080"), c.getEndpoint());
        assertTrue(c.isMasterProvisioning());
        assertEquals(8181, c.getMasterProvisioningBasePort());
        assertEquals("/tmp/masters", c.getMasterProvisioningHomeLocation());
        assertEquals(10, c.getMasterProvisioningTimeOut());
        assertEquals("provision", c.getMasterProvisioningScriptProvision());
        assertEquals("start", c.getMasterProvisioningScriptStart());
        assertEquals("stop", c.getMasterProvisioningScriptStop());
        assertEquals("terminate", c.getMasterProvisioningScriptTerminate());
    }

    private void testNoProperties(final Config c) throws Exception {
        _testWithThrower(new Thrower() {
            public void run() throws Exception {
                c.getEndpoint();
            }
        }, IllegalStateException.class);

        assertFalse(c.isMasterProvisioning());

        assertEquals(9090, c.getMasterProvisioningBasePort());

        _testWithThrower(new Thrower() {
            public void run() throws Exception {
                c.getMasterProvisioningHomeLocation();
            }
        }, IllegalStateException.class);

        assertEquals(60, c.getMasterProvisioningTimeOut());

        _testWithThrower(new Thrower() {
            public void run() throws Exception {
                c.getMasterProvisioningScriptProvision();
            }
        }, IllegalStateException.class);

        _testWithThrower(new Thrower() {
            public void run() throws Exception {
                c.getMasterProvisioningScriptStart();
            }
        }, IllegalStateException.class);

        _testWithThrower(new Thrower() {
            public void run() throws Exception {
                c.getMasterProvisioningScriptStop();
            }
        }, IllegalStateException.class);

        _testWithThrower(new Thrower() {
            public void run() throws Exception {
                c.getMasterProvisioningScriptTerminate();
            }
        }, IllegalStateException.class);
    }


    interface Thrower {
        void run() throws Exception;
    }
    private void _testWithThrower(Thrower x, Class<? extends Exception> ec) throws Exception {
        Exception ex = null;
        try {
            x.run();
        } catch(Exception e) {
            ex = e;
        }

        assertNotNull(ex);
        assertTrue("Not an instance of " + ec, ec.isInstance(ex));
    }


}
