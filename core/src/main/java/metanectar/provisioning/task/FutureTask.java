package metanectar.provisioning.task;

import java.util.concurrent.Future;

/**
 * @author Paul Sandoz
 */
public abstract class FutureTask<F, T extends Task> implements Task<T> {

    protected Future<F> future;

    public FutureTask() {
    }

    public FutureTask(Future<F> future) {
        this.future = future;
    }

    public boolean isStarted() {
        return future != null;
    }

    public boolean isDone() {
        return future.isDone();
    }

}
