package metanectar.agent;

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
}