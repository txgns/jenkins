package metanectar.provisioning;

import java.io.Serializable;
import java.util.concurrent.Future;

/**
 * @author Kohsuke Kawaguchi
 */
public class ProvisioningInProgress implements Serializable {
    public final String displayName;
    public final int numOfExecutors;
    public final Future<ProvisioningResult> future;

    public ProvisioningInProgress(String displayName, int numOfExecutors, Future<ProvisioningResult> future) {
        this.displayName = displayName;
        this.numOfExecutors = numOfExecutors;
        this.future = future;
    }

    private static final long serialVersionUID = 1L;
}
