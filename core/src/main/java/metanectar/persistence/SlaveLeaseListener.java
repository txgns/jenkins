package metanectar.persistence;

import com.google.common.collect.MapMaker;

import java.io.Serializable;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Allows obtaining futures for changes to the database.
 */
public class SlaveLeaseListener {

    private static final ConcurrentMap<String, FutureImpl> futures = new MapMaker().weakValues().makeMap();

    /*package*/ static void notifyChanged(String leaseId) {
        FutureImpl future = futures.remove(leaseId);
        if (future != null) {
            future.complete();
        }
    }

    private static void tidyUpFuture(String leaseId) {
        FutureImpl future = futures.get(leaseId);
        if (future.isDone()) {
            futures.remove(leaseId, future);
        }
    }

    public static Future<String> onChange(String leaseId) {
        while (true) {
            FutureImpl future = futures.get(leaseId);
            if (future == null) {
                futures.putIfAbsent(leaseId, new FutureImpl(leaseId));
            } else if (future.isDone()) {
                futures.replace(leaseId, future, new FutureImpl(leaseId));
            } else {
                return future;
            }
        }
    }

    public static void cancelAll() {
        while (!futures.isEmpty()) {
            Iterator<FutureImpl> i = futures.values().iterator();
            //noinspection WhileLoopReplaceableByForEach
            while (i.hasNext()) {
                FutureImpl entry = i.next();
                if (entry.isDone()) {
                    tidyUpFuture(entry.key);
                } else {
                    entry.cancel(true);
                }
            }
        }
    }

    public static class FutureImpl implements Future<String>, Serializable {

        public FutureImpl(String key) {
            this.key = key;
        }

        private static enum State {
            WAITING,
            CANCELLED,
            DONE
        }

        private State state = State.WAITING;

        private final String key;

        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            if (state == State.WAITING) {
                state = State.CANCELLED;
                notifyAll();
                return true;
            }
            return false;
        }

        public synchronized void complete() {
            if (!isDone()) {
                state = State.DONE;
                notifyAll();
            }
        }

        public synchronized boolean isCancelled() {
            return state == State.CANCELLED;
        }

        public synchronized boolean isDone() {
            return state != State.WAITING;
        }

        public synchronized String get() throws InterruptedException, ExecutionException {
            while (!isDone()) {
                wait();
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
            tidyUpFuture(key);
            if (isCancelled()) {
                throw new CancellationException("Task was cancelled.");
            }
            return key;
        }

        public synchronized String get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            long endWaiting = System.nanoTime() + unit.toNanos(timeout);
            while (!isDone() && System.nanoTime() < endWaiting) {
                long remaining = endWaiting - System.nanoTime();
                wait(remaining / 1000000L, (int) (remaining % 1000000));
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
            if (!isDone()) {
                throw new TimeoutException();
            }
            tidyUpFuture(key);
            if (isCancelled()) {
                throw new CancellationException("Task was cancelled.");
            }
            return key;
        }
    }
}
