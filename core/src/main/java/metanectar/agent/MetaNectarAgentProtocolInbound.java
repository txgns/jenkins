package metanectar.agent;

import metanectar.agent.Agent.AgentException;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * The MetaNectar agent protocol receives requires from the Jenkins/Nectar agent
 * to establish a channel.
 *
 * @author Paul Sandoz
 */
public class MetaNectarAgentProtocolInbound implements AgentProtocol.Inbound {

    public interface ApprovalListener {
        void onApprove(Object cert) throws GeneralSecurityException;
    }

    private ApprovalListener al;

    public MetaNectarAgentProtocolInbound(ApprovalListener al) {
        this.al = al;
    }

    public String getName() {
        return "Protocol:MetaNectar";
    }

    public void process(Connection connection) throws IOException, InterruptedException {
        // Get the certificate

        // Check if the Jenkins/Nectar agent is authorized to connect based on the certificate
        try {
            al.onApprove(null);
        } catch (GeneralSecurityException ex) {
            // TODO can we distinguish between a legitimate but unapproved request, to return some useful information
            //      and a bad request where no information should be given as to why approval failed
            // Handshake fails
            throw new AgentException(ex);
        }

        // Find JenkinsServer instance based on certificate
        // set channel on that instance
    }
}
