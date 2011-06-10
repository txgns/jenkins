package metanectar.provisioning;

import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.RetentionStrategy;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;

import static hudson.util.TimeUnit2.SECONDS;
import static java.util.logging.Level.WARNING;

/**
 * A retention strategy for shared slaves.
 */
public class SharedSlaveRetentionStrategy extends RetentionStrategy<AbstractCloudComputer> implements Serializable {

    public synchronized long check(AbstractCloudComputer c) {
        if (c.isIdle() && !disabled) {
            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if (idleMilliseconds > SECONDS.toMillis(10)) {
                LOGGER.info("Disconnecting " + c.getName());
                try {
                    c.getNode().terminate();
                } catch (InterruptedException e) {
                    LOGGER.log(WARNING, "Failed to terminate " + c.getName(), e);
                } catch (IOException e) {
                    LOGGER.log(WARNING, "Failed to terminate " + c.getName(), e);
                }
            }
        }
        return 1;
    }

    /**
     * Try to connect to it ASAP.
     */
    @Override
    public void start(AbstractCloudComputer c) {
        c.connect(false);
    }

    private static final Logger LOGGER = Logger.getLogger(SharedSlaveRetentionStrategy.class.getName());

    public static boolean disabled = Boolean.getBoolean(SharedSlaveRetentionStrategy.class.getName() + ".disabled");
}

