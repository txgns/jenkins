package metanectar.agent;

//import hudson.remoting.Channel;

import hudson.util.IOUtils;
import hudson.util.NullStream;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * An agent that establishes a connection on a TCP socket by agreeing to a certain
 * protocol handshake with an {@link AgentListener}.
 * <p>
 * This class is based on {@link hudson.remoting.Engine} and is designed to be backwards
 * compatible.
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
 */
public class Agent implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(Agent.class.getName());

    /**
     *
     */
    public static class AgentException extends IOException {
        public AgentException(String message) {
            super(message);
        }

        public AgentException(String message, Throwable cause) {
            super(message, cause);
        }

        public AgentException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * A resolver of of a connection
     */
    public static interface ConnectionResolver {

        /**
         * Resolve to a connection reference.
         *
         * @return the connection reference, otherwise null if the connection cannot be resolved to a reference.
         *
         * @throws IOException if an error occurs while resolving.
         */
        InetSocketAddress resolve() throws IOException;
    }

    private final AgentStatusListener listener;

    private final ConnectionResolver connectionResolver;

    private final List<? extends AgentProtocol> protocols;

    private boolean noReconnect = false;

    private int soTimeout = 30*60*1000;

    private int retryInterval = 1000*10;

    /**
     * Create a new channel agent.
     *
     * @param listener the listener to receive status notifications.
     * @param connectionResolver the connection resolver to obtain a connection reference that is used to open a
     *        TCP socket.
     * @param protocols the list of protocols that are to be tried, in order, to establish a channel.
     */
    public Agent(AgentStatusListener listener,
                 ConnectionResolver connectionResolver,
                 List<AgentProtocol.Outbound> protocols) {
        this.listener = listener;
        this.connectionResolver = connectionResolver;
        this.protocols = protocols;
    }

    public Agent(AgentStatusListener listener,
                 ConnectionResolver connectionResolver,
                 AgentProtocol.Outbound... protocols) {
        this(listener,connectionResolver, Arrays.asList(protocols));
    }

    /**
     * Get the reconnection behavior.
     * <p>
     * Default value is false;
     *
     * @return true if no reconnection should occur, otherwise false.
     */
    public boolean isNoReconnect() {
        return noReconnect;
    }

    /**
     * Set the reconnection behavior.
     * <p>
     *
     * @param noReconnect if true no reconnection will occur if channel cannot be established or
     *        the connection to an established channel is broken, otherwise reconnection will occur
     *        indefinitely (with delays between retries) unless an error occurs.
     */
    public void setNoReconnect(boolean noReconnect) {
        this.noReconnect = noReconnect;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    public int getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval;
    }

    public void run() {
        try {
            while(true) {
                final InetSocketAddress adrs = connectionResolver.resolve();
                if (adrs == null) {
                    listener.error(new AgentException("Cannot resolve host and point to establish connection with server"));

                    // Do not retry if connection cannot be resolved
                    return;
                }

                connectOnce(adrs);

                if(noReconnect)
                    return;

                Thread.sleep(retryInterval);
            }
        } catch (Throwable t) {
            listener.error(t);
        }
    }

    public boolean connectOnce(InetSocketAddress adrs) throws Exception {
        for (final AgentProtocol p : protocols) {
            listener.status("Handshaking protocol: " + p.getName());

            final Socket s = connect(adrs);
            final Connection con = new Connection(s);
            con.setListener(listener);

            // Write the protocol name
            con.writeUTF(p.getName());

            // Check the response
            if (!(p instanceof LegacyAgentProtocol)) {
                String response = con.readUTF();
                if (!response.equals("ACK")) {
                    listener.error(new AgentException("Unsupported protocol " + p.getName()));
                    s.close();
                    return false;
                }
            }

            try {
                p.process(con);
                return true;
            } finally {
                try {
                    // let the input side open so that the other side will have time to read whatever we sent
                    s.shutdownOutput();
                    IOUtils.copy(s.getInputStream(), new NullStream());
                    s.close();
                } catch (IOException e) {
                    // ignore
                    e.printStackTrace();
                }
            }
//            Map<String, Object> props = p.handshake(listener);
//            if (props != null) {
//                p.process(listener, props, s.getInputStream(), s.getOutputStream());
//                return true;
//            } else {
//                listener.error(new AgentException("The handshake could not be agreed for the protocol " + p.getName()));
//                s.close();
//                return false;
//            }
        }
        return false;
    }

    private Socket connect(InetSocketAddress adrs) throws IOException, InterruptedException {
        final String msg = "Connecting to " + adrs;
        listener.status(msg);
        int retry = 1;
        while (true) {
            try {
                final Socket s = new Socket(adrs.getAddress(), adrs.getPort());
                s.setTcpNoDelay(true); // we'll do buffering by ourselves

                // set read time out to avoid infinite hang. the time out should be long enough so as not
                // to interfere with normal operation. the main purpose of this is that when the other peer dies
                // abruptly, we shouldn't hang forever, and at some point we should notice that the connection
                // is gone.
                s.setSoTimeout(soTimeout);
                return s;
            } catch (IOException e) {
                if(retry++ > 10)
                    throw (IOException)new IOException("Failed to connect to " + adrs).initCause(e);
                Thread.sleep(retryInterval);
                listener.status(msg+" (retrying:" + retry + ")", e);
            }
        }
    }
}
