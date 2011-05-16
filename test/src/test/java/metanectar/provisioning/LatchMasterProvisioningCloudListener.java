package metanectar.provisioning;

import hudson.model.Node;
import hudson.slaves.Cloud;
import metanectar.cloud.MasterProvisioningCloudListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Paul Sandoz
 */

public abstract class LatchMasterProvisioningCloudListener extends MasterProvisioningCloudListener {
    protected final CountDownLatch cdl;

    public LatchMasterProvisioningCloudListener(int i) {
        MasterProvisioningCloudListener.all().add(0, this);
        cdl = new CountDownLatch(i);
    }

    public void await(long timeout, TimeUnit unit) throws InterruptedException {
        cdl.await(timeout, unit);
    }

    public void countDown() {
        cdl.countDown();
    }

    public long getCount() {
        return cdl.getCount();
    }

    public static class ProvisionListener extends LatchMasterProvisioningCloudListener {
        public ProvisionListener(int i) {
            super(i);
        }

        public void onProvisioning(Cloud c) {
            countDown();
        }

        public void onProvisioned(Cloud c, Node n) {
            countDown();
        }
    }

    public static class TerminateListener extends LatchMasterProvisioningCloudListener {
        public TerminateListener(int i) {
            super(i);
        }

        public void onTerminated(Node n) {
            countDown();
        }
    }

}
