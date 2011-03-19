package metanectar.agent;

import hudson.util.IOUtils;
import hudson.util.NullStream;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens to connections from agents to establish a TCP connection.
 * <p>
 * This class is based on {@link hudson.TcpSlaveAgentListener} and is designed to be backwards
 * compatible.
 *
 * TODO this is not going to scale since it uses a thread per connection.
 * At some point this will need to change to using something like Grizzly or Netty and
 * non-blocking IO.
 * hudson.remoting.Channel will also require changes to support non-blocking.
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
 */
public class AgentListener implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(AgentListener.class.getName());

    private static AtomicInteger iotaGen = new AtomicInteger(1);

    private final AgentStatusListener listener;

    private final int port;

    private final Map<String, AgentProtocol> protocols;

    private final ServerSocket serverSocket;

    private volatile boolean shuttingDown;

    /**
     *
     * @param listener
     * @param port
     * @param protocols
     * @throws IOException
     */
    public AgentListener(AgentStatusListener listener,
                         int port,
                         List<? extends AgentProtocol.Inbound> protocols) throws IOException {
        this.listener = listener;
        this.port = port;
        this.protocols = new HashMap<String, AgentProtocol>();
        for (AgentProtocol p : protocols) {
            this.protocols.put(p.getName(), p);
        }

        try {
            this.serverSocket = new ServerSocket(port);
        } catch (BindException e) {
            throw (BindException)new BindException("Failed to listen on port "+port+" because it's already in use.").initCause(e);
        }
    }

    /**
     *
     * @return
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void run() {
        LOGGER.info("Agent listener started on TCP port " + getPort());

        try {
            // the loop eventually terminates when the socket is closed.
            while (true) {
                final Socket s = serverSocket.accept();

                // this prevents a connection from silently terminated by the router in between or the other peer
                // and that goes without unnoticed. However, the time out is often very long (for example 2 hours
                // by default in Linux) that this alone is enough to prevent that.
                s.setKeepAlive(true);

                new ConnectionHandler(s).start();
            }
        } catch (IOException e) {
            if (!shuttingDown) {
                LOGGER.log(Level.SEVERE,"Failed to accept JNLP slave agent connections",e);
            }
        }
    }

    /**
     * Initiates the shuts down of the listener.
     */
    public void shutdown() {
        shuttingDown = true;
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close down TCP port", e);
        }
    }

    private final class ConnectionHandler extends Thread {
        private final Socket s;
        /**
         * Unique number to identify this connection. Used in the log.
         */
        private final int id;

        public ConnectionHandler(Socket s) {
            this.s = s;
            this.id = iotaGen.getAndIncrement();
            setName("Agent connection handler #" + id + " with " + s.getRemoteSocketAddress());
        }

        @Override
        public void run() {
            boolean success=false;
            try {
                LOGGER.info("Accepted connection #" + id + " from " + s.getRemoteSocketAddress());

                final Connection con = new Connection(s);
                con.setListener(listener);

                // Read the protocol identifier
                final String protocolIdentifier = con.readUTF();

                // Look up the protocol
                AgentProtocol p = protocols.get(protocolIdentifier);
                if (p != null) {
                    LOGGER.info("Handshaking on connection #" + id + " for protocol " + p.getName());
                    if (!(p instanceof LegacyAgentProtocol)) {
                        con.writeUTF("ACK");
                    }

                    p.process(con);
                    success = true;
                } else {
                    error(con.dout, "Unknown protocol");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,"Connection #"+ id +" failed", e);
            } finally {
                if (!success)
                    try {
                        // let the input side open so that the other side will have time to read whatever we sent
                        s.shutdownOutput();
                        IOUtils.copy(s.getInputStream(), new NullStream());
                        s.close();
                    } catch (IOException e) {
                        // ignore
                    }
            }
        }

        private void error(DataOutputStream dos, String msg) throws IOException {
            dos.writeUTF(msg);
            LOGGER.log(Level.WARNING, "Connection #" + id + " is aborted: " + msg);
        }
    }
}