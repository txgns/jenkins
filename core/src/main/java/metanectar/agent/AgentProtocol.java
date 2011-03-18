package metanectar.agent;

import java.io.*;
import java.util.Map;

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

    public static interface Inbound extends AgentProtocol {};

    public static interface Outbound extends AgentProtocol {};

    /**
     * Get the name of the protocol.
     *
     * @return the name of the protocol.
     */
    String getName();

    /**
     * Perform a protocol handshake.
     *
     * @param l the status listener
     * @param din the data input stream to read handshaking inbound data.
     * @param dos the data output stream to write handshaking outbound data.
     * @return a non-null, possibly empty, map of properties if the handshaking has been agreed, otherwise null.
     * @throws java.io.IOException if an error occurs when handshaking.
     */
    Map<String, Object> handshake(AgentStatusListener l, DataInputStream din, DataOutputStream dos) throws IOException;

    /**
     *
     * @param l
     * @param ps
     * @param in
     * @param out
     * @throws IOException
     * @throws InterruptedException
     */
    void process(AgentStatusListener l, Map<String, Object> ps, InputStream in, OutputStream out) throws IOException, InterruptedException;
}
