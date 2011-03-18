package metanectar.agent;

/**
 * A marker interface to be implemented by {@link AgentProtocol} to signal legacy protocol exchange
 * were it is up to the handshaking to manage the initial acknowledge of the protocol.
 * <p>
 * This is required for backwards compatibility for the protocols "Protocol: JNLP-connect", "Protocol: JNLP2-connect"
 * and "Protocol: CLI-connect".
 *
 * @author Paul Sandoz
 */
public interface LegacyAgentProtocol {
}
