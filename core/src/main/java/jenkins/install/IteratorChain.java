package jenkins.install;

import java.util.Iterator;

import org.apache.commons.lang.NotImplementedException;

import com.google.common.base.Function;

/**
 * Simple chain pattern using iterator.next()
 */
public class IteratorChain<T> implements Iterator<T> {
    private final Iterator<Function<Iterator<T>,T>> functions;
    public IteratorChain(Iterator<Function<Iterator<T>,T>> functions) {
        this.functions = functions;
    }
    @Override
    public boolean hasNext() {
        return functions.hasNext();
    }
    @Override
    public T next() {
        return functions.next().apply(this);
    }
    @Override
    public void remove() {
        throw new NotImplementedException();
    }
}
