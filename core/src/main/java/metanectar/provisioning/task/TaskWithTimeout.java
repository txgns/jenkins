package metanectar.provisioning.task;

import hudson.util.Futures;

import java.util.concurrent.Future;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class TaskWithTimeout<F, T extends Task> implements Task<F,T> {
    private final long timeout;

    public TaskWithTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getTimeout() {
        return timeout;
    }

    public final Future<F> start() throws Exception {
        return Futures.withTimeout(doStart(),timeout);
    }

    protected abstract Future<F> doStart() throws Exception;
}
