package metanectar;

import com.google.common.collect.Maps;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration properties for MetaNectar.
 * <p>
 * Properties will be loaded from a URL, if it exists, whose location is declared by the system property
 * "METANECTAR_PROPERTIES_URL".
 * <p>
 * If a property does not exist in the properties obtained from the URL, then system properties will be checked.
 *
 * @author Paul Sandoz
 */
public class Config {

    public static final String METANECTAR_PROPERTIES_URL_SYSTEM_PROPERTY_NAME = "METANECTAR_PROPERTIES_URL";

    private static final Logger LOGGER = Logger.getLogger(Config.class.getName());

    private static final String METANECTAR_PROPERTIES_URL = System.getProperty(METANECTAR_PROPERTIES_URL_SYSTEM_PROPERTY_NAME);

    private final Map<String, String> properties = Maps.newConcurrentMap();

    /* protected */ Config() {
        load(METANECTAR_PROPERTIES_URL);
    }

    /* protected */ Config(String propertiesUrl) {
        load(propertiesUrl);
    }

    private static class SingletonHolder {
      public static final Config INSTANCE = new Config();
    }

    public static Config getInstance() {
      return SingletonHolder.INSTANCE;
    }

    private void load(String propertiesUrl) {
        if (propertiesUrl == null) {
            return;
        }

        try {
            Properties ps = new Properties();
            ps.load(new URL(propertiesUrl).openStream());
            for (Map.Entry e : ps.entrySet()) {
                properties.put((String)e.getKey(), (String)e.getValue());
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading properties file \"" + propertiesUrl + "\"", e);
        }
    }

    /**
     * @return the property "metaNectar.endpoint" that is the URL endpoint of MetaNectar
     * @throws MalformedURLException if the property is not a valid URL
     * @throws IllegalStateException if the property is not present.
     */
    public URL getEndpoint() throws MalformedURLException, IllegalStateException {
        return new URL(getProperty("metaNectar.endpoint"));
    }

    /**
     * @return the property "metaNectar.isMasterProvisioning" that is true if MetaNectar is capable of master
     *         provisioning, otherwise false.
     */
    public boolean isMasterProvisioning() {
        return Boolean.valueOf(getProperty("metaNectar.isMasterProvisioning", "false"));
    }

    /**
     * @return the property "metaNectar.master.provisioning.basePort" that is the base port to use as the initial
     *         port for the first provisioned master. The default value is 9090.
     * @throws NumberFormatException if the property value is not an integer.
     */
    public int getMasterProvisioningBasePort() throws NumberFormatException {
        return Integer.valueOf(getProperty("metaNectar.master.provisioning.basePort", "9090"));
    }

    /**
     * @return the property "metaNectar.master.provisioning.homeLocation" that is the location where master home
     *         directories of provisioned masters will be created.
     * @throws IllegalStateException if the property is not present.
     */
    public String getMasterProvisioningHomeLocation() throws IllegalStateException {
        return getProperty("metaNectar.master.provisioning.homeLocation");
    }

    /**
     * @return the property "metaNectar.master.provisioning.timeOut" that is the time out value to use for any
     *         asynchronous master provisioning operations. The default value is 60 seconds, if the property is
     *         not present.
     * @throws NumberFormatException if the property value is not an integer.
     */
    public int getMasterProvisioningTimeOut() throws NumberFormatException {
        return Integer.valueOf(getProperty("metaNectar.master.provisioning.timeOut", "60"));
    }

    /**
     * @return the property "metaNectar.master.provisioning.script.provision" that is the name of the script to execute
     *         when provisioning a master.
     * @throws IllegalStateException if the property is not present.
     */
    public String getMasterProvisioningScriptProvision() throws IllegalStateException {
        return getProperty("metaNectar.master.provisioning.script.provision");
    }

    /**
     * @return the property "metaNectar.master.provisioning.script.terminate" that is the name of the script to execute
     *         when terminating a master.
     * @throws IllegalStateException if the property is not present.
     */
    public String getMasterProvisioningScriptTerminate() throws IllegalStateException {
        return getProperty("metaNectar.master.provisioning.script.terminate");
    }

    private String getProperty(String name) throws IllegalStateException {
        return getProperty(name, null);
    }

    private String getProperty(String name, String defaultValue) throws IllegalStateException {
        String value = properties.get(name);

        if (value == null) {
            value = System.getProperty(name);
            if (value != null) {
                properties.put(name, value);
            }
        }

        if (value != null) {
            value = value.trim();
            if (value.isEmpty())
                value = null;
        }

        if (value == null && defaultValue == null) {
            throw new IllegalStateException("The property name \"" + name + "\" must be defined in the properties file or as a system property of the same name");
        } else if (value == null) {
            value = defaultValue;
        }

        return value;
    }
}