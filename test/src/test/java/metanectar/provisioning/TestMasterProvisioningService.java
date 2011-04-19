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

    public Future<Master> provision(final VirtualChannel channel, TaskListener listener,
                                    int id, final String organization, final URL metaNectarEndpoint, final Map<String, Object> properties) throws IOException, InterruptedException {
        return Computer.threadPoolForRemoting.submit(new Callable<Master>() {
            public Master call() throws Exception {
                System.out.println("Launching master " + organization);

                Thread.sleep(delay);

                final URL endpoint = channel.call(new TestMasterServerCallable(metaNectarEndpoint, organization, properties));

                System.out.println("Launched master " + organization + ": " + endpoint);
                provisioned.put(organization, endpoint);
                return new Master(organization, endpoint);
            }
        });
    }

    public Future<?> terminate(VirtualChannel channel, TaskListener listener,
                               final String organization, boolean clean) throws IOException, InterruptedException {
        return Computer.threadPoolForRemoting.submit(new Callable<Void>() {
            public Void call() throws Exception {
                URL endpoint = provisioned.get(organization);

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
            return masterServer.start();
        }
    }
}
