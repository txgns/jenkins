package metanectar.provisioning.task;

import java.util.concurrent.Future;

/**
 * @author Paul Sandoz
 */
public abstract class FutureTask<F, T extends Task> implements Task<T> {

    private final long timeout;

    private Future<F> future;

    private long startTime;

    public FutureTask(long timeout) {
        this.timeout = timeout;
    }

    public long getTimeout() {
        return timeout;
    }

    protected Future<F> getFuture() {
        return future;
    }

    protected void setFuture(Future<F> future) {
        this.startTime = System.currentTimeMillis();
        this.future = future;
    }

    public boolean isStarted() {
        return future != null;
    }

    public boolean isDone() {
        // Try to cancel the future if the task is running longer than the timeout duration
        if ((startTime + timeout) < System.currentTimeMillis()) {
            future.cancel(true);
        }

        return future.isDone();
    }

}
