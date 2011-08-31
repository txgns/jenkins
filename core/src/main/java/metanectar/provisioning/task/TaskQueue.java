package metanectar.provisioning.task;

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

    public static final class TaskHolder<T extends Task> {
        private final T t;
        private final FutureTaskEx<?> ft;

        private TaskHolder(T t, FutureTaskEx<?> ft) {
            this.t = t;
            this.ft = ft;
        }

        public T getTask() {
            return t;
        }

        public Future<?> getFuture() {
            return ft;
        }
    }

    private final ConcurrentLinkedQueue<TaskHolder<T>> queue = new ConcurrentLinkedQueue<TaskHolder<T>>();

    public void process() {
        for (Iterator<TaskHolder<T>> itr = queue.iterator(); itr.hasNext(); ) {
            final TaskHolder<T> th = itr.next();
            final T t = th.t;
            final FutureTaskEx<?> ft = th.ft;

            if (!t.isStarted()) {
                try {
                    t.start();
                } catch (Exception e) {
                    itr.remove();
                    ft.run();
                    continue;
                }
            }

            if (t.isDone()) {
                T next = null;
                try {
                    next = (T) t.end();
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
            t.start();
            queue.add(new TaskHolder(t, ft));
        } catch (Exception e) {
            ft.run();
        }

        return ft;
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
     *      --- see {@link Task#end()}), this future task is synchronously executed and the value
     *      of type V is computed, and made available through the future object this method returns.
     * @return
     *      The future that waits for the completion of the task and the given {@link FutureTaskEx}.
     */
    public <V> Future<V> add(T t, FutureTaskEx<V> ft) {
        queue.add(new TaskHolder(t, ft));
        return ft;
    }

    /**
     * Adds the task to the queue, and returns a future that yields the given pre-existing value
     * when the given task is fully completed.
     */
    public <V> Future<V> add(T t, V value) {
        return add(t,FutureTaskEx.createValueFutureTask(value));
    }


    public ConcurrentLinkedQueue<TaskHolder<T>> getQueue() {
        return queue;
    }
}
