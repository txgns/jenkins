package metanectar.agent;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Map;

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

    public Map<String, Object> handshake(AgentStatusListener l, DataInputStream din, DataOutputStream dos) throws IOException {
        // Get the certificate

        // Check if the Jenkins/Nectar agent is authorized to connect based on the certificate
        try {
            al.onApprove(null);
        } catch (GeneralSecurityException ex) {
            l.status("Not approved", ex);

            // TODO can we distinguish between a legitimate but unapproved request, to return some useful information
            //      and a bad request where no information should be given as to why approval failed

            // Handshake fails
            return null;
        }

        // Handshake succeeds
        return Collections.emptyMap();
    }

    public void process(AgentStatusListener l, Map<String, Object> ps, InputStream in, OutputStream out) throws IOException, InterruptedException {

        // Find JenkinsServer instance based on certificate
        // set channel on that instance
    }
}
