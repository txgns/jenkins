package metanectar.provisioning.task;

import java.util.concurrent.Future;

/**
 * @author Paul Sandoz
 */
public interface Task<T extends Task> {
    /**
     * Returns true if the {@link #start()} method is called and the task has started.
     */
    boolean isStarted();

    /**
     * Starts the execution of this task.
     * <p>
     * This method is expected to just initiate asynchronous execution of some work,
     * (instead of synchronously executing it inside this method)
     */
    void start() throws Exception;

    /**
     * Periodically called by {@link TaskQueue} to see if this task has finished execution.
     */
    boolean isDone();

    /**
     * If the task has finished executing, {@link TaskQueue} will call this method (to indicate
     * that the queue has acknowledge its completion.)
     *
     * @return
     *      If a non-null value is returned, the returned task needs to be completed before the
     *      {@linkplain FutureTaskEx callback waiting for the task} will be notified.
     */
    T end() throws Exception;

    /**
     * Returns true if the task was cancelled before it completed normally.
     * (There can be some delay between the invocation of the {@link #cancel()} method and
     * this method starting to return true.
     */
    boolean isCancelled();

    /**
     * Attempts to cancel the asynchronous execution of the task.
     *
     * @see Future#cancel(boolean)
     * @return
     *      true if an attempt was made to cancel the task. false if the task is known not to be cancellable.
     */
    boolean cancel();
}
