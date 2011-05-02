package metanectar.provisioning.task;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Paul Sandoz
 */
public class TaskQueue<T extends Task> {

    private LinkedList<T> queue;

    public TaskQueue() {
        this.queue = new LinkedList<T>();
    }

    public void process() {
        for (Iterator<T> itr = queue.iterator(); itr.hasNext();) {
            final T t = itr.next();

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
                    try {
                        next.start();
                        add(t);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            } else if (!t.isStarted()) {
                try {
                    t.start();
                } catch (Exception e) {
                    itr.remove();
                }
            }
        }
    }

    public void add(T t) {
        queue.add(t);
    }

    public List<T> getQueue() {
        return queue;
    }
}
