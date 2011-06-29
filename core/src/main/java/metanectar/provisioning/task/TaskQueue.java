package metanectar.provisioning.task;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Paul Sandoz
 */
public class TaskQueue<T extends Task> {

    private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<T>();

    public void process() {
        for (Iterator<T> itr = queue.iterator(); itr.hasNext();) {
            final T t = itr.next();

            if (!t.isStarted()) {
                try {
                    t.start();
                } catch (Exception e) {
                    itr.remove();
                    continue;
                }
            }

            if (t.isDone()) {
                T next = null;
                try {
                    next = (T)t.end();
                } catch (Exception e) {
                    // Ignore
                } finally {
                    itr.remove();
                }

                if (next != null) {
                    start(next);
                }
            }
        }
    }

    public void start(T t) {
        try {
            t.start();
            queue.add(t);
        } catch (Exception e) {
            // Ignore
        }
    }

    public void add(T t) {
        queue.add(t);
    }

    public ConcurrentLinkedQueue<T> getQueue() {
        return queue;
    }
}
