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
                if (ft.isCancelled()) {
                    itr.remove();
                    continue;
                }

                try {
                    t.start();
                } catch (Exception e) {
                    itr.remove();
                    setExceptionOnStart(ft, e);
                    continue;
                }
            }

            if (t.isDone()) {
                T next = null;
                try {
                    next = (T) t.end();
                } catch (Exception e) {
                    setExceptionOnEnd(ft, e);
                    continue;
                } finally {
                    itr.remove();
                }

                if (next != null) {
                    start(next, ft);
                } else {
                    ft.run();
                }
            } else if (ft.isCancelled() && !t.isCancelled()) {
                t.cancel();
            }
        }
    }

    private void setExceptionOnStart(FutureTaskEx<?> ft, Throwable t) {
        ft._setException(t);
    }

    private void setExceptionOnEnd(FutureTaskEx<?> ft, Throwable t) {
        if (t instanceof ExecutionException) {
            ft._setException(t.getCause());
        } else if (t instanceof CancellationException) {
            ft._cancel(true);
        }  else {
            ft._setException(t);
        }
    }

    public Future<?> start(T t) {
        return start(t, FutureTaskEx.createNoOpFutureTask());
    }

    public <V> Future<V> start(T t, FutureTaskEx<V> ft) {
        try {
            t.start();
            queue.add(new TaskHolder(t, ft));
        } catch (Exception e) {
            ft._setException(e);
        }

        return ft;
    }

    public Future<?> add(T t) {
        return add(t, FutureTaskEx.createNoOpFutureTask());
    }

    public <V> Future<V> add(T t, FutureTaskEx<V> ft) {
        queue.add(new TaskHolder(t, ft));
        return ft;
    }

    public ConcurrentLinkedQueue<TaskHolder<T>> getQueue() {
        return queue;
    }
}
