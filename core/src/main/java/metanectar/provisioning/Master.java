package metanectar.provisioning;

import java.net.URL;

/**
 * The state of a provisioned master.
 *
 * @author Paul Sandoz
 */
public class Master {
    public final String organization;
    public final URL endpoint;

    public Master(String organization, URL endpoint) {
        this.organization = organization;
        this.endpoint = endpoint;
    }
}
