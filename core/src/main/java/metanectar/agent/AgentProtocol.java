package metanectar.agent;

import java.io.IOException;

/**
 * A protocol that may be agreed between an {@link Agent} and an {@link AgentListener}.
 * <p>
 * This interface is independent of inbound and outbound requests but an implementation will not be and must
 * be written for one or the other depending if the implementation is used with {@link Agent} or {@link AgentListener}
 * respectively.
 *
 * @author Paul Sandoz
 */
public interface AgentProtocol {

    public static interface Inbound extends AgentProtocol {}

    public static interface Outbound extends AgentProtocol {}

    /**
     * Get the name of the protocol.
     *
     * @return the name of the protocol.
     */
    String getName();

    void process(Connection connection) throws IOException, InterruptedException;
}
