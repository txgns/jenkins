package metanectar.provisioning;

import com.google.common.collect.Maps;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.IOUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
* Created by IntelliJ IDEA.
* User: sandoz
* Date: 4/6/11
* Time: 6:36 PM
* To change this template use File | Settings | File Templates.
*/
public class TestMasterProvisioningService extends MasterProvisioningService {

    private final int delay;

    private final Map<String, URL> provisioned = Maps.newHashMap();

    TestMasterProvisioningService(int delay) {
        this.delay = delay;
    }

    public int getDelay() {
        return delay;
    }

    public Future<Provisioned> provision(final VirtualChannel channel, TaskListener listener,
                                    int id, final String name, final URL metaNectarEndpoint, final Map<String, Object> properties) throws IOException, InterruptedException {
        return Computer.threadPoolForRemoting.submit(new Callable<Provisioned>() {
            public Provisioned call() throws Exception {
                System.out.println("Launching master " + name);

                Thread.sleep(delay);

                final URL endpoint = channel.call(new TestMasterServerCallable(metaNectarEndpoint, name, properties));

                Thread.sleep(delay);

                System.out.println("Launched master " + name + ": " + endpoint);
                provisioned.put(name, endpoint);
                return new Provisioned(name, endpoint);
            }
        });
    }

    public Future<?> start(VirtualChannel channel, TaskListener listener,
                                    final String name) throws Exception {
        return Computer.threadPoolForRemoting.submit(new Callable<Void>() {
            public Void call() throws Exception {
                URL endpoint = provisioned.get(name);

                URL start = new URL(endpoint.toExternalForm() + "/start");
                HttpURLConnection c = (HttpURLConnection)start.openConnection();
                c.setDoOutput(true);
                c.setRequestMethod("POST");
                IOUtils.toString(c.getInputStream());
                c.getResponseCode();

                return null;
            }
        });
    }

    public Future<?> stop(VirtualChannel channel, TaskListener listener,
                                    final String name) throws Exception {
        return Computer.threadPoolForRemoting.submit(new Callable<Void>() {
            public Void call() throws Exception {
                URL endpoint = provisioned.get(name);

                URL stop = new URL(endpoint.toExternalForm() + "/stop");
                HttpURLConnection c = (HttpURLConnection)stop.openConnection();
                c.setDoOutput(true);
                c.setRequestMethod("POST");
                IOUtils.toString(c.getInputStream());
                c.getResponseCode();

                return null;
            }
        });
    }

    public Future<Terminated> terminate(VirtualChannel channel, TaskListener listener,
                               final String name) throws IOException, InterruptedException {
        return Computer.threadPoolForRemoting.submit(new Callable<Terminated>() {
            public Terminated call() throws Exception {
                Thread.sleep(delay);

                return new Terminated(name, null);
            }
        });
    }

    public static class TestMasterServerCallable implements hudson.remoting.Callable<URL, Exception> {
        private final String organization;
        private final URL metaNectarEndpoint;
        private final Map<String, Object> properties;

        public TestMasterServerCallable(URL metaNectarEndpoint, String organization, Map<String, Object> properties) {
            this.organization = organization;
            this.metaNectarEndpoint = metaNectarEndpoint;
            this.properties = properties;
        }

        public URL call() throws Exception {
            TestMasterServer masterServer = new TestMasterServer(metaNectarEndpoint, organization, properties);
            masterServer.setRetryInterval(500);
            return masterServer.endpoint;
        }
    }
}
