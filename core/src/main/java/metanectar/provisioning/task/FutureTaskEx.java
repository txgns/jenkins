package metanectar.provisioning.task;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * An extension of future task to be associated with a {@link Task} on a {@link TaskQueue}.
 * <p>
 * Currently this implementation disables the ability to explicitly cancel the future and therefore the associated
 * {@link Task}.
 * </p>
 * @author Paul Sandoz
 */
public class FutureTaskEx<V> extends FutureTask<V> {

    public FutureTaskEx(Callable<V> callable) {
        super(callable);
    }

    public FutureTaskEx(Runnable runnable, V result) {
        super(runnable, result);
    }

    /**
     * @see FutureTask#setException(Throwable)
     */
    /* package */ void _setException(Throwable t) {
        setException(t);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    /* package */ boolean _cancel(boolean mayInterruptIfRunning) {
        return super.cancel(mayInterruptIfRunning);
    }

    /**
     * Create a {@link FutureTaskEx} whose callable returns immediately with a void result.
     *
     * @return a {@link FutureTaskEx} whose callable returns immediately with a void result.
     */
    public static FutureTaskEx<Void> createNoOpFutureTask() {
        return new FutureTaskEx<Void>(NO_OP_CALLABLE);
    }

    /**
     * Create a {@link FutureTaskEx} whose callable returns immediately with a value.
     *
     * @param t the value to return.
     * @return a {@link FutureTaskEx} whose callable returns immediately with the value.
     */
    public static <T> FutureTaskEx<T> createValueFutureTask(final T t) {
        return new FutureTaskEx<T>(new Callable<T>() {
            public T call() throws Exception {
                return t;
            }
        });
    }

    private static final Callable<Void> NO_OP_CALLABLE = new Callable<Void>() {
        public Void call() throws Exception {
            return null;
        }
    };
}
