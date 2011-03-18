package metanectar.agent;

import java.io.*;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;

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

    public Map<String, Object> handshake(AgentStatusListener l, DataInputStream din, DataOutputStream dos) throws IOException {

        // Send the certificate

        // check the response, if approved handshake succeeds, otherwise fails.

        return Collections.emptyMap();
    }

    public void process(AgentStatusListener l, Map<String, Object> ps, InputStream in, OutputStream out) throws IOException, InterruptedException {
        // Get the meta nectar root action instance
        // set the channel on that instance
    }
}
