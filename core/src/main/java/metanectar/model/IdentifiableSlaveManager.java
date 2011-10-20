package metanectar.model;

import com.cloudbees.commons.metanectar.provisioning.SlaveManager;

/**
 * A {@link SlaveManager} that has a unique identifier that can be used to find it again if it gets lost.
 */
public interface IdentifiableSlaveManager extends SlaveManager {
    String getUid();
}
