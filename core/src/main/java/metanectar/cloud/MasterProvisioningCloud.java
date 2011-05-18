package metanectar.cloud;

import hudson.slaves.Cloud;
import metanectar.MetaNectarExtensionPoint;

/**
 * A marker interface to signal a {@link Cloud} implementation is associated with master provisioning
 * and not slave provisioning.
 *
 * @author Paul Sandoz
 */
public interface MasterProvisioningCloud extends MetaNectarExtensionPoint { }
