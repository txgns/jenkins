package metanectar;

import com.google.common.collect.Maps;
import metanectar.property.DefaultValue;
import metanectar.property.Optional;
import metanectar.property.PropertiesToBeanMapper;
import metanectar.property.Property;

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

    private final Properties properties;

    private final PropertiesToBeanMapper binder;

    private final Map<Class, Object> bindCache = Maps.newConcurrentMap();

    public Config() {
        this(METANECTAR_PROPERTIES_URL);
    }

    public Config(String propertiesUrl) {
        this(load(propertiesUrl));
    }

    public Config(Properties properties) {
        this.properties = properties;
        this.binder = new PropertiesToBeanMapper(properties);
    }

    private static class SingletonHolder {
        public static final Config INSTANCE = new Config();
    }

    public static Config getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static Properties load(String propertiesUrl) {
        final Properties ps = new Properties();

        if (propertiesUrl == null) {
            return ps;
        }

        try {
            ps.load(new URL(propertiesUrl).openStream());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading properties file \"" + propertiesUrl + "\"", e);
        }

        return ps;
    }


    public static class MetaNectarProperties {
        private URL endpoint;

        public URL getEndpoint() {
            return endpoint;
        }

        @Property("metaNectar.endpoint")
        public void setEndpoint(URL endpoint) {
            this.endpoint = endpoint;
        }
    }

    public static class MetaNectarProvisioningProperties {
        private boolean isMasterProvisioning;

        private String remoteFS;

        public boolean isMasterProvisioning() {
            return isMasterProvisioning;
        }

        @Property("metaNectar.isMasterProvisioning") @DefaultValue("false")
        public void setMasterProvisioning(boolean masterProvisioning) {
            isMasterProvisioning = masterProvisioning;
        }

        @Property("metaNectar.master.node.remoteFS") @Optional
        public void setRemoteFS(String remoteFS) {
            this.remoteFS = remoteFS;
        }
    }

    public <T> T getBean(Class<T> c) {
        if (bindCache.containsKey(c)) {
            return (T)bindCache.get(c);
        }

        T t = binder.mapTo(c);
        bindCache.put(t.getClass(), t);

        return t;
    }

    /**
     * @return the property "metaNectar.endpoint" that is the URL endpoint of MetaNectar
     */
    public URL getEndpoint() {
        return getBean(MetaNectarProperties.class).endpoint;
    }

    /**
     * @return the property "metaNectar.isMasterProvisioning" that is true if MetaNectar is capable of master
     *         provisioning, otherwise false.
     */
    public boolean isMasterProvisioning() {
        return getBean(MetaNectarProvisioningProperties.class).isMasterProvisioning;
    }

    /**
     * @return the property "metaNectar.master.node.remoteFS" that is the remote file system location on the master
     * node that may be used by MetaNectar when establishing a connection, otherwise null if not set.
     */
    public String getRemoteFS() {
        return getBean(MetaNectarProvisioningProperties.class).remoteFS;
    }

    private String getProperty(String name) throws IllegalStateException {
        return getProperty(name, null);
    }

    private String getProperty(String name, String defaultValue) throws IllegalStateException {
        String value = properties.getProperty(name);

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