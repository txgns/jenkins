package metanectar.provisioning;

import hudson.model.Computer;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
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

    TestMasterProvisioningService(int delay) {
        this.delay = delay;
    }

    public Future<Master> provision(final VirtualChannel channel, final String organization, final URL metaNectarEndpoint, final Map<String, String> properties) throws IOException, InterruptedException {
        return Computer.threadPoolForRemoting.submit(new Callable<Master>() {
            public Master call() throws Exception {
                System.out.println("Launching master " + organization);

                Thread.sleep(delay);

                final URL endpoint = channel.call(new TestMasterServerCallable(metaNectarEndpoint, organization, properties));

                System.out.println("Launched master " + organization + ": " + endpoint);
                return new Master(organization, endpoint);
            }
        });
    }

    public Future<?> terminate(VirtualChannel channel, String organization, boolean clean) throws IOException, InterruptedException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Map<String, Master> getProvisioned(VirtualChannel channel) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public static class TestMasterServerCallable implements hudson.remoting.Callable<URL, Exception> {
        private final String organization;
        private final URL metaNectarEndpoint;
        private final Map<String, String> properties;

        public TestMasterServerCallable(URL metaNectarEndpoint, String organization, Map<String, String> properties) {
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
