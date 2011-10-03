package metanectar.provisioning.task;

import java.util.concurrent.Future;

/**
 * @author Paul Sandoz
 */
public interface Task<F, T extends Task> {
    /**
     * Starts the execution of this task.
     * <p>
     * This method is expected to just initiate asynchronous execution of some work,
     * (instead of synchronously executing it inside this method)
     */
    Future<F> start() throws Exception;

    /**
     * If the task has finished executing, {@link TaskQueue} will call this method (to indicate
     * that the queue has acknowledge its completion.)
     *
     * @return
     *      If a non-null value is returned, the returned task needs to be completed before the
     *      {@linkplain FutureTaskEx callback waiting for the task} will be notified.
     */
    T end(Future<F> execution) throws Exception;
}
