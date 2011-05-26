package metanectar.proxy;

import com.google.common.util.concurrent.Futures;
import hudson.Proc;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import metanectar.model.AbstractMasterServerListener;
import metanectar.model.MasterServer;
import metanectar.model.MetaNectar;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * A listener that prods the reserve proxy to reload it's routes when a master is
 * provisioned or terminated.
 *
 * @author Paul Sandoz
 */
public class ReverseProxyProdder extends AbstractMasterServerListener {

    private final MetaNectar mn;

    private final String reload;

    private Future currentProd = Futures.immediateFuture(null);

    private boolean resubmitProd = false;

    private final Object lock = new Object();

    private int requestedProdCount;

    private int actualProdCount;

    public ReverseProxyProdder(MetaNectar mn, String reload) {
        this.mn = mn;
        this.reload = reload;
    }

    public int getRequestedProdCount() {
        return requestedProdCount;
    }

    public int getActualProdCount() {
        return actualProdCount;
    }

    public void onProvisioned(MasterServer ms) {
        prod();
    }

    public void onTerminated(MasterServer ms) {
        prod();
    }

    private void prod() {
        synchronized (lock) {
            requestedProdCount++;
            if (currentProd.isDone()) {
                submit();
            } else {
                resubmitProd = true;
            }
        }
    }

    private void submit() {
        currentProd = Computer.threadPoolForRemoting.submit(getCallable());
        resubmitProd = false;
        actualProdCount++;
    }

    private Callable<Void> getCallable() {
        return new Callable<Void>() {
            public Void call() throws Exception {
                final TaskListener listener = StreamTaskListener.fromStdout();

                listener.getLogger().println(String.format("Executing reverse proxy reload command \"%s\"", reload));
                final Proc reloadProcess = mn.createLauncher(listener).launch().
                        cmds(Util.tokenize(reload)).
                        stderr(listener.getLogger()).
                        stdout(listener.getLogger()).
                        start();

                final int result = reloadProcess.joinWithTimeout(60, TimeUnit.SECONDS, listener);

                if (result != 0) {
                    listener.error("Failed to execute reverse proxy reload command, received signal: " + result);
                } else {
                    listener.getLogger().println("Reverse proxy reload command succeeded");
                }

                synchronized (lock) {
                    if (resubmitProd) {
                        // Wait a little to avoid too many sequential requests
                        try {
                            lock.wait(200);
                        } catch (InterruptedException ex) {
                        }

                        // Re-submit
                        submit();
                    }
                }
                return null;
            }
        };
    }
}
