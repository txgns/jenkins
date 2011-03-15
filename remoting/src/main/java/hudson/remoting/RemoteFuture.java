package hudson.remoting;

import java.io.Serializable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps a {@link java.util.concurrent.Future} to be remoting safe.
 *
 * @author Kohsuke Kawaguchi
 */
public class RemoteFuture<V> implements Future<V>, Serializable {
    private final java.util.concurrent.Future<V> core;

    public RemoteFuture(java.util.concurrent.Future<V> core) {
        this.core = core;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return core.cancel(mayInterruptIfRunning);
    }

    public boolean isCancelled() {
        return core.isCancelled();
    }

    public boolean isDone() {
        return core.isDone();
    }

    public V get() throws InterruptedException, ExecutionException {
        return core.get();
    }

    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return core.get(timeout, unit);
    }

    private Object writeReplace() {
        final Channel ch = Channel.current();
        if (ch!=null)
            return new FutureProxy(ch.export(Future.class, this));
        else
            return this;
    }

    private static final long serialVersionUID = 1L;
}
