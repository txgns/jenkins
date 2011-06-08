package metanectar;

import metanectar.model.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Paul Sandoz
 */
public abstract class LatchConnectedMasterListener extends ConnectedMasterListener {
    protected final CountDownLatch cdl;

    public LatchConnectedMasterListener(int i) {
        ConnectedMasterListener.all().add(0, this);
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

    public static class ConnectedListener extends LatchConnectedMasterListener {
        public ConnectedListener(int i) {
            super(i);
        }

        @Override
        public void onConnected(ConnectedMaster cm) {
            countDown();
        }

        @Override
        public void onDisconnected(ConnectedMaster cm) {
        }
    }

    public static class DisconnectedListener extends LatchConnectedMasterListener {
        public DisconnectedListener(int i) {
            super(i);
        }

        @Override
        public void onConnected(ConnectedMaster cm) {
        }

        @Override
        public void onDisconnected(ConnectedMaster cm) {
            countDown();
        }
    }
}
