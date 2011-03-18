package metanectar.agent;

import java.io.IOException;

/**
 * The Jenkins/Nectar protocol that establishes the channel with MetaNectar.
 *
 * @author Paul Sandoz
 */
public class MetaNectarAgentProtocolOutbound implements AgentProtocol.Outbound {
    public MetaNectarAgentProtocolOutbound() {
    }

    public String getName() {
        return "Protocol:MetaNectar";
    }

    public void process(Connection connection) throws IOException, InterruptedException {

        // Send the certificate

        // check the response, if approved handshake succeeds, otherwise fails.

        // Get the meta nectar root action instance
        // set the channel on that instance
    }
}
