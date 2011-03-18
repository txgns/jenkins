package metanectar.agent;

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
     * Name that uniquely identifies this protocol among all the other protocol implementations.
     * <p>
     * This is used in the early protocol selection step so that the receiver understands how to interprect
     * the inbound connection.
     *
     * @return
     *      The name of the protocol. Need not be human readable.
     */
    String getName();

    /**
     * After the initial protocol selection, the connection is handed over to this method
     * of the right {@link AgentProtocol}.
     *
     * @throws Exception
     *      If the protocol terminates abnormally, throw some exception. The caller
     *      will report that exception to {@link AgentStatusListener#error(Throwable)}
     */
    void process(Connection connection) throws Exception;
}
