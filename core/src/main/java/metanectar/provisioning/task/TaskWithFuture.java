package metanectar.provisioning.task;

import java.util.concurrent.Future;

/**
 * {@link Task} that can provide {@link Future} that represents its asynchronous operation.
 *
 * <blockquote>
 * Kohsuke notes:
 * I don't see any reason why this abstraction shouldn't be merged into {@link Task} by simplifying
 * Task to just have a "{@code Future start()}" method. Why duplicate most of the {@link Future} into
 * Task?
 * </blockquote>
 *
 *
 * @author Paul Sandoz
 */
public abstract class TaskWithFuture<F, T extends Task> implements Task<T> {

    private final long timeout;

    private Future<F> future;

    private long startTime;

    public TaskWithFuture(long timeout) {
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
            cancel();
        }

        return future.isDone();
    }

    public boolean isCancelled() {
        if (!isStarted())
            return false;

        return future.isCancelled();
    }

    public boolean cancel() {
        if (!isStarted())
            return false;

        return future.cancel(true);
    }

}
