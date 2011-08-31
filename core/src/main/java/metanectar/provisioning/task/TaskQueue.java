package metanectar.provisioning.task;

import com.google.common.util.concurrent.Futures;

import java.util.Iterator;
import java.util.concurrent.*;

/**
 *
 * TODO cancellation of the FutureTaskEx tracking will not work correctly for the following cases:
 * 1) if the task cannot be cancelled; or
 * 2) the task has already completed.
 * until there is some synchronization between the FutureTaskEx and the Task. Explicit cancellation
 * of FutureTaskEx has been disabled until the synchronization is implemented.
 *
 * @author Paul Sandoz
 */
public class TaskQueue<T extends Task> {
    /**
     * Represents a task and its execution state in the queue.
     *
     * As a {@link Future} it represents the whole chain of event from the dispatching
     * of a chain of tasks to the invocation of the completion handler.
     */
    public final class TaskHolder<V> implements Future<V> {
        private final T t;

        /**
         * When non-null, represents the fact that the task is currently executing (or has finished executing.)
         */
        volatile Future<?> execution;

        /**
         * Callback that gets invoked when the chain of tasks has fully finished executing.
         */
        private final FutureTask<V> ft;

        private TaskHolder(T t, FutureTask<V> ft) {
            this.t = t;
            this.ft = ft;
        }

        public T getTask() {
            return t;
        }

        public Future<?> getFuture() {
            return ft;
        }

        public boolean isStarted() {
            return execution!=null;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            Future<?> e = execution;
            return e != null && e.cancel(mayInterruptIfRunning);
        }

        public boolean isCancelled() {
            Future<?> e = execution;
            return e != null && e.isCancelled();
        }

        public boolean isDone() {
            Future<?> e = execution;
            return e != null && e.isDone();
        }

        public V get() throws InterruptedException, ExecutionException {
            return ft.get();
        }

        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return ft.get(timeout,unit);
        }
    }

    private final ConcurrentLinkedQueue<TaskHolder<?>> queue = new ConcurrentLinkedQueue<TaskHolder<?>>();

    public void process() {
        for (Iterator<TaskHolder<?>> itr = queue.iterator(); itr.hasNext(); ) {
            final TaskHolder<?> th = itr.next();
            final T t = th.t;
            final FutureTask<?> ft = th.ft;

            if (!th.isStarted()) {
                try {
                    th.execution = t.start();
                } catch (Exception e) {
                    itr.remove();
                    ft.run();
                    continue;
                }
            }

            if (th.execution.isDone()) {
                T next = null;
                try {
                    next = (T)t.end(th.execution);
                } catch (Exception e) {
                    ft.run();
                    continue;
                } finally {
                    itr.remove();
                }

                if (next != null) {
                    start(next, ft);
                } else {
                    ft.run();
                }
            }
        }
    }

    public Future<?> start(T t) {
        return start(t, FutureTaskEx.createNoOpFutureTask());
    }

    /**
     * Adds the task to the queue and starts it right away.
     */
    public <V> Future<V> start(T t, FutureTaskEx<V> ft) {
        try {
            TaskHolder<V> h = new TaskHolder<V>(t, ft);
            h.execution = t.start();
            queue.add(h);
            return h;
        } catch (Exception e) {
            ft.run();
            return ft;  // ft conveniently represents a future object that's already completed and returns ft.get()
        }
    }

    public <V> Future<V> start(T t, V value) {
        return start(t,FutureTaskEx.createValueFutureTask(value));
    }

    public Future<?> add(T t) {
        return add(t, FutureTaskEx.createNoOpFutureTask());
    }

    /**
     * Adds the task to the queue.
     *
     * @param t
     *      Task. This gets started eventually.
     * @param ft
     *      When the task runs its course (including all the successor tasks that it designated
     *      --- see {@link Task#end(Future)}), this future task is synchronously executed and the value
     *      of type V is computed, and made available through the future object this method returns.
     * @return
     *      The future that waits for the completion of the task and the given {@link FutureTaskEx}.
     */
    public <V> Future<V> add(T t, FutureTask<V> ft) {
        TaskHolder<V> h = new TaskHolder<V>(t, ft);
        queue.add(h);
        return h;
    }

    /**
     * Adds the task to the queue, and returns a future that yields the given pre-existing value
     * when the given task is fully completed.
     */
    public <V> Future<V> add(T t, V value) {
        return add(t,FutureTaskEx.createValueFutureTask(value));
    }


    public ConcurrentLinkedQueue<TaskHolder<?>> getQueue() {
        return queue;
    }
}
