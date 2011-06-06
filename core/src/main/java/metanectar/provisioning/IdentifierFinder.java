package metanectar.provisioning;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * @author Paul Sandoz
 */
public abstract class IdentifierFinder<T> {

    private final Comparator c = new Comparator<T>() {
        public int compare(T t1, T t2) {
            return getId(t1) - getId(t2);
        }
    };

    public int getUnusedIdentifier(final List<T> l) {
        // Empty
        if (l.isEmpty())
            return 0;

        // One Element
        if (l.size() == 1) {
            final T t = l.get(0);
            return (getId(t) > 0) ? 0 : getId(t) + 1;
        }

        // Multiple elements, sort by id then find a gap in the intervals
        Collections.sort(l, c);

        // If there are no gaps
        if (l.size() == getId(l.get(l.size() - 1)) + 1) {
            return l.size();
        }

        final Iterator<T> ti = l.iterator();
        T start = ti.next();
        T end = null;
        while (ti.hasNext()) {
            end = ti.next();

            if (getId(end) - getId(start) > 1) {
                return getId(start) + 1;
            }

            start = end;
        }

        return getId(end) + 1;
    }

    protected abstract int getId(T t);
}
