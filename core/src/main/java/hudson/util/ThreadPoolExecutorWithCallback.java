/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.util;

import hudson.remoting.AsyncFutureImpl;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link ExecutorService} decorator that returns {@link Future} that allows registration of callbacks,
 * which are fired when the task is completed.
 *
 * <p>
 * Stop-gap till Guava r10 releases, at which point we should switch to ListenableFuture and its related abstractions.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
public class ThreadPoolExecutorWithCallback implements ExecutorService {

    public interface Callback<V> {
        void onCompleted(Future<V> v);
    }

    public interface FutureWithCallback<V> extends Future<V> {
        void addCallback(Callback<V> callback);
    }

    private static final class FutureImpl<V> extends AsyncFutureImpl<V> implements FutureWithCallback<V> {
        private List<Callback<V>> callbacks = new ArrayList<Callback<V>>();

        /**
         * Represents the real computation. Needed for cancelling.
         * This future is marked as complete before we invoke callbacks, so do not rely on its return value.
         */
        private Future<?> base;

        public synchronized void addCallback(Callback<V> c) {
            if (callbacks==null) { // task is already complete. fire callback immediately
                c.onCompleted(this);
            } else {
                callbacks.add(c);
            }
        }

        public boolean isCancelled() {
            return base.isCancelled();
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = base.cancel(mayInterruptIfRunning);
            if (cancelled) {
                // Subsequent calls to base.cancel should return false
                setAsCancelled();

                // Invoking the callbacks on the thread that invoked the cancel, not good!, should be run from the
                // the executor that was running the cancelled task
                invokeCallbacks();
            }

            return cancelled;
        }

        public void invokeCallbacks() {
            List<Callback<V>> callbacks;
            // atomically swap registered callback handlers so that racing addCallback() will fire the callback by itself
            synchronized (this) {
                callbacks = this.callbacks;
                this.callbacks = null;
            }

            if (callbacks==null)    return; // already fired

            // TODO surround by try/catch to stop Throwables leaking out?
            for (Callback<V> c : callbacks)
                c.onCompleted(this);
        }
    }


    private final ExecutorService core;

    public ThreadPoolExecutorWithCallback(ExecutorService core) {
        this.core = core;
    }

    public void shutdown() {
        core.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return core.shutdownNow();
    }

    public boolean isShutdown() {
        return core.isShutdown();
    }

    public boolean isTerminated() {
        return core.isTerminated();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return core.awaitTermination(timeout, unit);
    }

    public <T> Future<T> submit(final Callable<T> task) {
        final FutureImpl<T> r = new FutureImpl<T>();
        Callable<Void> wrapper = new Callable<Void>() {
            public Void call() throws Exception {
                try {
                    r.set(task.call());
                } catch (Throwable t) {
                    if (!r.base.isCancelled()) {
                        // Avoid the case when an InterruptedException is thrown as a result of the callable
                        // being cancelled, this assumes that the cancellation state of the future is set before
                        // that exception occurs.
                        r.set(t);
                    }
                } finally {
                    if (!r.base.isCancelled()) {
                        // Only invoke callbacks if not cancelled
                        // Invocation of callbacks when the cancelled cannot reliably be performed in the callable
                        // wrapper, since it is not guaranteed that an exception will be thrown.
                        r.invokeCallbacks();
                    }
                }
                return null;
            }
        };
        r.base = core.submit(wrapper);
        return r;
    }

    public <T> Future<T> submit(Runnable task, T result) {
        return submit(Executors.callable(task,result));
    }

    public Future<?> submit(Runnable task) {
        return submit(Executors.callable(task));
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return core.invokeAll(tasks);
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return core.invokeAll(tasks, timeout, unit);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return core.invokeAny(tasks);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return core.invokeAny(tasks, timeout, unit);
    }

    public void execute(Runnable command) {
        core.execute(command);
    }
}
