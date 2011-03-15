package hudson.remoting;

import java.io.Serializable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link Future} that delegates to the remote future proxy.
 *
 * @author Kohsuke Kawaguchi
 */
class FutureProxy<V> implements Future<V>, Serializable {
    /**
     * Proxy to the remote future object.
     */
    private final java.util.concurrent.Future<V> proxy;

    /**
     * If we got the result back, this is the result value.
     */
    private Object value;

    /**
     * If we know that this future is cancelled already, set to true.
     */
    private boolean cancelled;

    FutureProxy(java.util.concurrent.Future<V> core) {
        this.proxy = core;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return proxy.cancel(mayInterruptIfRunning);
    }

    public boolean isCancelled() {
        return cancelled || (cancelled=proxy.isCancelled());
    }

    public boolean isDone() {
        return value!=null;
    }

    public V get() throws InterruptedException, ExecutionException {
        if (value!=null)    return getValue();
        return store(proxy.get());
    }

    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (value!=null)    return getValue();
        return store(proxy.get(timeout,unit));
    }

    private V store(V v) {
        value = (v==null) ? NULL : v;
        return v;
    }

    private V getValue() {
        return value == NULL ? null : (V)value;
    }

    private static final Object NULL = new Object();

    private static final long serialVersionUID = 1L;
}
