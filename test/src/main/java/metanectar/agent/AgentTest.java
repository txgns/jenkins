package metanectar.agent;

import hudson.remoting.Callable;
import hudson.remoting.Channel;
import junit.framework.TestCase;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Paul Sandoz
 */
public class AgentTest extends TestCase {

    private static class CurrentChannelCallable implements Callable<String, IOException> {
        public String call() throws IOException {
            return Channel.current().toString();
        }
    }


    private Agent.ConnectionResolver getResolver(final int port) {
        return new Agent.ConnectionResolver() {
            public Agent.ConnectionReference resolve() throws IOException {
                return new Agent.ConnectionReference("localhost", port) {
                    public void ping() throws IOException {

                    }
                };
            }
        };
    }

    private AgentStatusListener getValidListener(final AtomicInteger errors) {
        return new AgentStatusListener() {
            public void status(String msg) {
                System.err.println(msg);
            }

            public void status(String msg, Throwable t) {
                System.err.println(msg);
                t.printStackTrace();
            }

            public void error(Throwable t) {
                t.printStackTrace();
                errors.incrementAndGet();
            }
        };

    }

    private AgentStatusListener getInvalidListener(final AtomicInteger errors) {
        return new AgentStatusListener() {
            public void status(String msg) {
                System.err.println(msg);
            }

            public void status(String msg, Throwable t) {
                System.err.println(msg);
                t.printStackTrace();
            }

            public void error(Throwable t) {
                t.printStackTrace();
                errors.incrementAndGet();
                assertTrue(t instanceof Agent.AgentException);
            }
        };

    }

    public void testValidHandshake() throws Exception {

        final AtomicInteger errors = new AtomicInteger(0);

        AgentStatusListener asl = getValidListener(errors);

        AgentProtocol.Inbound inbound = new AgentProtocol.Inbound() {
            public String getName() {
                return "TEST PROTOCOL";
            }

            public Map<String, Object> handshake(AgentStatusListener l, DataInputStream din, DataOutputStream dos) throws IOException {
                String s = din.readUTF();
                assertEquals("TEST", s);

                dos.writeUTF("OK");
                return Collections.emptyMap();
            }

            public void process(AgentStatusListener l, Map<String, Object> ps, InputStream in, OutputStream out) throws IOException, InterruptedException {
            }
        };

        final AgentListener al = new AgentListener(asl, 0, Collections.singletonList(inbound));

        AgentProtocol.Outbound outbound = new AgentProtocol.Outbound() {
            public String getName() {
                return "TEST PROTOCOL";
            }

            public Map<String, Object> handshake(AgentStatusListener l, DataInputStream din, DataOutputStream dos) throws IOException {
                dos.writeUTF("TEST");

                String s = din.readUTF();
                assertEquals("OK", s);
                return Collections.emptyMap();
            }

            public void process(AgentStatusListener l, Map<String, Object> ps, InputStream in, OutputStream out) throws IOException, InterruptedException {
            }
        };

        Agent a = new Agent(asl, getResolver(al.getPort()), Collections.singletonList(outbound));
        a.setNoReconnect(true);

        new Thread(al).start();

        a.run();

        assertEquals(0, errors.get());
    }

    public void testUnknownProtocol() throws Exception {

        final AtomicInteger errors = new AtomicInteger(0);

        AgentStatusListener asl = getInvalidListener(errors);

        final AtomicInteger i = new AtomicInteger(0);

        AgentProtocol.Inbound inbound = new AgentProtocol.Inbound() {
            public String getName() {
                return "TEST PROTOCOL1";
            }

            public Map<String, Object> handshake(AgentStatusListener l, DataInputStream din, DataOutputStream dos) throws IOException {
                assertTrue(false);
                return null;
            }

            public void process(AgentStatusListener l, Map<String, Object> ps, InputStream in, OutputStream out) throws IOException, InterruptedException {
                assertTrue(false);
            }
        };

        final AgentListener al = new AgentListener(asl, 0, Collections.singletonList(inbound));

        AgentProtocol.Outbound outbound = new AgentProtocol.Outbound() {
            public String getName() {
                return "TEST PROTOCOL2";
            }

            public Map<String, Object> handshake(AgentStatusListener l, DataInputStream din, DataOutputStream dos) throws IOException {
                assertTrue(false);
                return null;
            }

            public void process(AgentStatusListener l, Map<String, Object> ps, InputStream in, OutputStream out) throws IOException, InterruptedException {
                assertTrue(false);
            }
        };

        Agent a = new Agent(asl, getResolver(al.getPort()), Collections.singletonList(outbound));
        a.setNoReconnect(true);

        new Thread(al).start();

        a.run();

        assertEquals(1, errors.get());
    }

    public void testUnknownLegacyProtocol() throws Exception {

        final AtomicInteger errors = new AtomicInteger(0);

        AgentStatusListener asl = getInvalidListener(errors);

        AgentProtocol.Inbound inbound = new AgentProtocol.Inbound() {
            public String getName() {
                return "TEST PROTOCOL1";
            }

            public Map<String, Object> handshake(AgentStatusListener l, DataInputStream din, DataOutputStream dos) throws IOException {
                assertTrue(false);
                return null;
            }

            public void process(AgentStatusListener l, Map<String, Object> ps, InputStream in, OutputStream out) throws IOException, InterruptedException {
                assertTrue(false);
            }
        };

        final AgentListener al = new AgentListener(asl, 0, Collections.singletonList(inbound));

        AgentProtocol.Outbound outbound = new AgentProtocol.Outbound() {
            public String getName() {
                return "TEST PROTOCOL2";
            }

            public Map<String, Object> handshake(AgentStatusListener l, DataInputStream din, DataOutputStream dos) throws IOException {
                String response = din.readUTF();
                assertEquals(response, "Unknown Protocol");
                if (!response.equals("Welcome")) {
                    l.error(new Agent.AgentException("The server rejected the connection:: " + response));
                    return null;
                }
                return Collections.emptyMap();
            }

            public void process(AgentStatusListener l, Map<String, Object> ps, InputStream in, OutputStream out) throws IOException, InterruptedException {
                assertTrue(false);
            }
        };

        Agent a = new Agent(asl, getResolver(al.getPort()), Collections.singletonList(outbound));
        a.setNoReconnect(true);

        new Thread(al).start();

        a.run();

        assertEquals(1, errors.get());
    }


    public void testChannelEstablishment() throws Exception {

        final ExecutorService executor = Executors.newCachedThreadPool();

        final AtomicInteger errors = new AtomicInteger(0);

        AgentStatusListener asl = getValidListener(errors);

        AgentProtocol.Inbound inbound = new AgentProtocol.Inbound() {
            public String getName() {
                return "TEST PROTOCOL";
            }

            public Map<String, Object> handshake(AgentStatusListener l, DataInputStream din, DataOutputStream dos) throws IOException {
                return Collections.emptyMap();
            }

            public void process(AgentStatusListener l, Map<String, Object> ps, InputStream in, OutputStream out) throws IOException, InterruptedException {
                final Channel channel = new Channel("inbound-channel", executor,
                        new BufferedInputStream(in),
                        new BufferedOutputStream(out));
                channel.join();
            }
        };

        final AgentListener al = new AgentListener(asl, 0, Collections.singletonList(inbound));

        AgentProtocol.Outbound outbound = new AgentProtocol.Outbound() {
            public String getName() {
                return "TEST PROTOCOL";
            }

            public Map<String, Object> handshake(AgentStatusListener l, DataInputStream din, DataOutputStream dos) throws IOException {
                return Collections.emptyMap();
            }

            public void process(AgentStatusListener l, Map<String, Object> ps, InputStream in, OutputStream out) throws IOException, InterruptedException {
                final Channel channel = new Channel("outbound-channel", executor,
                        new BufferedInputStream(in),
                        new BufferedOutputStream(out));

                String channelName = channel.call(new CurrentChannelCallable());

                assertTrue(channelName.contains("inbound-channel"));
            }
        };

        Agent a = new Agent(asl, getResolver(al.getPort()), Collections.singletonList(outbound));
        a.setNoReconnect(true);

        new Thread(al).start();

        a.run();

        assertEquals(0, errors.get());
    }

}
