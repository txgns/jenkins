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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (getClass() != o.getClass()) return false;

        Master master = (Master) o;

        return organization.equals(master.organization);
    }

    @Override
    public int hashCode() {
        return organization.hashCode();
    }
}
