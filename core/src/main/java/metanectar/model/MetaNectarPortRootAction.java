package metanectar.model;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;

/**
 * A hidden and unprotected root action that returns the HTTP header declare the port
 * a master may use for an agent to connect.
 *
 * @author Paul Sandoz
 */
@Extension
public class MetaNectarPortRootAction implements UnprotectedRootAction {
    public static final String URL_NAME = "metaNectarPort";

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return URL_NAME;
    }
}
