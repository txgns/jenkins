package metanectar.provisioning.task;

import hudson.model.Hudson;
import hudson.model.RestartListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import metanectar.model.MasterServer;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Paul Sandoz
 */
public class MasterWaitForQuietDownTask extends MasterServerTask<Boolean> {
    private static final Logger LOGGER = Logger.getLogger(MasterWaitForQuietDownTask.class.getName());

    private boolean cancelled;

    public MasterWaitForQuietDownTask(long timeout, MasterServer ms) {
        super(timeout, ms, MasterServer.Action.Stop);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public Future<Boolean> doStart() throws Exception {
        try {
            LOGGER.info("Waiting to quiet down for master " + ms.getName());

            // Set the waiting for quiet down state
            ms.setWaitingForQuietDownState();

            final Channel c = ms.getChannel();
            return c.callAsync(new WaitForQuietDown(getTimeout()));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Waiting to quiet down error for master" + ms.getName(), e);

            ms.setReapprovedState(e);
            throw e;
        }
    }

    public MasterServerTask end(Future<Boolean> f) throws Exception {
        try {
            cancelled = f.get();

            if (cancelled) {
                LOGGER.info("Waiting to quiet down cancelled for master " + ms.getName());

                // Inform that quiet down was cancelled
                ms.setReapprovedState(new CancellationException("Quiet down was cancelled"));
            } else {
                LOGGER.info("Waiting to quiet down completed for master " + ms.getName());
                ms.setReapprovedState();
            }

        } catch (Exception e) {
            final Throwable cause = (e instanceof ExecutionException) ? e.getCause() : e;

            LOGGER.log(Level.WARNING, "Waiting to quiet down error for master" + ms.getName(), cause);

            ms.setReapprovedState(cause);
            throw e;
        }

        return null;
    }


    public static final class WaitForQuietDown implements Callable<Boolean, Throwable> {
        private long timeout;

        public WaitForQuietDown(long timeout) {
            this.timeout = timeout;
        }

        public Boolean call() throws Throwable {
            final Hudson h = Hudson.getInstance();

            h.doQuietDown();

            if (timeout > 0) timeout += System.currentTimeMillis();
            while (h.isQuietingDown()
                   && (timeout <= 0 || System.currentTimeMillis() < timeout)
                   && !RestartListener.isAllReady()) {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            }

            return !h.isQuietingDown();
        }
    }

    public static final class CancelQuietDown implements Callable<Void, Throwable> {
        public Void call() throws Throwable {
            final Hudson h = Hudson.getInstance();

            if (h.isQuietingDown()) {
                h.doCancelQuietDown();
            }

            return null;
        }
    }
}
