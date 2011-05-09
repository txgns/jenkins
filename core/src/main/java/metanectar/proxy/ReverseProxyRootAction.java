package metanectar.proxy;

import com.google.common.base.Function;
import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A hidden and unprotected root action that provides the URLs of provisioned masters to a reverse proxy
 * such that the proxy can route requests using a uniform and clean URL pattern that not refer to specific
 * hosts.
 *
 * @author Paul Sandoz
 */
@Extension
public class ReverseProxyRootAction implements UnprotectedRootAction {
    public static final String URL_NAME = "reverse-proxy";

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return URL_NAME;
    }

    @ExportedBean(defaultVisibility=999)
    public static class Masters {
        Map<String, Master> instances;

        public Masters() {
            this.instances = new HashMap<String, Master>();
        }

        @Exported
        public Map<String, Master> getInstances() {
            return instances;
        }

        public void add(Master m) {
            instances.put(m.getName(), m);
        }
    }


    @ExportedBean(defaultVisibility=999)
    public static class Master {
        String name;

        String state;

        String url;

        public Master(String name, String state, String url) {
            this.name = name;
            this.state = state;
            this.url = url;
        }

        public String getName() {
            return name;
        }

        @Exported
        public String getState() {
            return state;
        }

        @Exported
        public String getUrl() {
            return url;
        }
    }

    /**
     * Bound to the URL /reverse-proxy/routes
     * <p>
     *
     */
    public void doRoutes(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        final Masters masters = new Masters();

        final Function<MasterServer, Void> f = new Function<MasterServer, Void>() {
            public Void apply(final MasterServer master) {
                if (master.isProvisioned()) {
                    masters.add(new Master(master.getName(), master.getState().name(), master.getEndpoint().toExternalForm()));
                }
                return null;
            }
        };

        for (MasterServer master : MetaNectar.getInstance().getMasters()) {
            master.query(f);
        }

        rsp.serveExposedBean(req, masters, Flavor.JSON);
    }
}