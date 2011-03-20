package metanectar.agent;

import hudson.remoting.Channel;
import hudson.remoting.PingThread;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;

/**
 * TODO channel.isInClosed() is package protected
 *
 * @author Kohsuke Kawaguchi, Paul Sandoz
*/
public abstract class InboundChannelAgentProtocol implements AgentProtocol.Inbound {

    /**
     *
     */
    public static interface ChannelProtocolListener {
        /**
         * Called when a connection is established.
         */
        void onConnect(Channel c);

        /**
         * Called when a connection is terminated.
         */
        void onDisconnect();

    }

    private final ExecutorService executor;

    private final ChannelProtocolListener cl;

    private int pingInterval = 10*60*1000;

    /**
     *
     * @param executor the executor service to use with an established channel.
     * @param cl the channel protocol listener to report connection and disconnection events.
     */
    public InboundChannelAgentProtocol(ExecutorService executor, ChannelProtocolListener cl) {
        this.executor = executor;
        this.cl = cl;
    }

    public int getPingInterval() {
        return pingInterval;
    }

    public void setPingInterval(int pingInterval) {
        this.pingInterval = pingInterval;
    }

    protected Channel channel;

    public void process(Connection con) throws Exception {
        this.channel = new Channel("ichannel", executor,
                new BufferedInputStream(con.in),
                new BufferedOutputStream(con.out));

        final PingThread t = new PingThread(channel, pingInterval) {
            protected void onDead() {
//                try {
//                    if (!channel.isInClosed()) {
//                        LOGGER.info("Ping failed. Terminating the socket.");
//                        s.close();
//                    }
//                } catch (IOException e) {
//                    LOGGER.log(SEVERE, "Failed to terminate the socket", e);
//                }
            }
        };
        t.start();

        con.getListener().status("Connected");
        cl.onConnect(channel);
        try {
            channel.join();
        } finally {
            con.getListener().status("Terminated");
            t.interrupt();  // make sure the ping thread is terminated
            cl.onDisconnect();
        }
    }


}
