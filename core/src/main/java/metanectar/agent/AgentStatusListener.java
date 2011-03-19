package metanectar.agent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Receives status notification from {@link Agent} or {@AgentListener}.
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
 */
public interface AgentStatusListener {
    /**
     * Status message that indicates the progress of the operation.
     */
    void status(String msg);

    /**
     * Status message, with additional stack trace that indicates an error that was recovered.
     */
    void status(String msg, Throwable t);

    /**
     * Fatal error that's non recoverable.
     */
    void error(Throwable t);

    public static class LoggerListener implements AgentStatusListener {
        private final Logger logger;

        public LoggerListener(Logger logger) {
            this.logger = logger;
        }

        public void status(String msg) {
            logger.info(msg);
        }

        public void status(String msg, Throwable t) {
            logger.log(Level.INFO,msg,t);
        }

        public void error(Throwable t) {
            logger.log(Level.WARNING,t.getMessage(),t);
        }
    }
}