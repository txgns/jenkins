package metanectar.provisioning.task;

import com.google.common.util.concurrent.Futures;
import hudson.util.ThreadPoolExecutorWithCallback;
import hudson.util.ThreadPoolExecutorWithCallback.FutureWithCallback;

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
     *
     * TaskHolder goes through four state.
     * First it gets created,
     * then it gets started (starting this state, execution!=null),
     * then it gets completed (starting this state, isDone()==true),
     * then it gets joined (starting this state, joined==true)
     *
     * During the transition from created to started, the asynchronous computation gets going.
     * The completion of that triggers transition from started to completed. This either happens
     * through polling, or via callback of {@link FutureWithCallback}.
     * The transition from completed to joined will fire off a chained task, if needed.
     * Once the task gets to the joined state, it can be removed from {@link TaskQueue#queue} any time.
     *
     * State transition is guarded by the synchronized(this) to ensure atomicity.
     */
    public final class TaskHolder<V> implements Future<V> {
        private final T t;

        /**
         * When non-null, represents the fact that the task is currently executing (or has finished executing.)
         */
        private volatile Future<?> execution;

        /**
         * Callback that gets invoked when the chain of tasks has fully finished executing, and
         * defines the ultimate result (the output from Future.)
         */
        private final FutureTask<V> ft;

        private volatile boolean joined;

        private TaskHolder(T t, FutureTask<V> ft) {
            this.t = t;
            this.ft = ft;
        }

        public T getTask() {
            return t;
        }

        public Future<V> getFuture() {
            return ft;
        }

        private synchronized void start() {
            if (isStarted())    throw new IllegalStateException();
            try {
                execution = t.start();
                if (execution instanceof FutureWithCallback) {
                    // if the future supports callback, use that to quickly move to the completed state
                    ((FutureWithCallback)execution).addCallback(new ThreadPoolExecutorWithCallback.Callback() {
                        public void onCompleted(Future v) {
                            join();
                        }
                    });
                }
            } catch (Exception e) {
                // if the task failed to start, then this holder goes straight to the joined state
                // ft.run() is called by design --- the handler get to know whether or not the start worked or failed.
                execution = ft;
                joined = true;
                ft.run();
            }
        }

        private synchronized void join() {
            if (!isDone())    throw new IllegalStateException();
            if (joined) return;
            joined = true;

            T next;
            try {
                next = (T)t.end(execution);
            } catch (Exception e) {
                ft.run();
                return;
            }

            if (next != null) {
                TaskQueue.this.add(next, ft);
            } else {
                ft.run();
            }
        }

        public boolean isStarted() {
            return execution!=null;
        }

        public boolean isJoined() {
            return joined;
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

    /**
     * Push holders to the next state via polling.
     */
    public void process() {
        for (Iterator<TaskHolder<?>> itr = queue.iterator(); itr.hasNext(); ) {
            final TaskHolder<?> th = itr.next();

            if (!th.isStarted())
                th.start();

            if (th.isDone())
                th.join();

            if (th.isJoined())
                itr.remove();
        }
    }

    public TaskHolder<?> start(T t) {
        return start(t, FutureTaskEx.createNoOpFutureTask());
    }

    /**
     * Adds the task to the queue and starts it right away.
     */
    public <V> TaskHolder<V> start(T t, FutureTaskEx<V> ft) {
        TaskHolder<V> h = add(t,ft);
        h.start();
        return h;
    }

    public <V> TaskHolder<V> start(T t, V value) {
        return start(t,FutureTaskEx.createValueFutureTask(value));
    }

    public TaskHolder<?> add(T t) {
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
    public <V> TaskHolder<V> add(T t, FutureTask<V> ft) {
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
