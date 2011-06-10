package metanectar.model;

import com.google.common.base.Predicate;
import com.sun.istack.internal.Nullable;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.slaves.ComputerLauncher;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Checks that the supplied descriptors are not on the blacklist.
 *
 * @author Stephen Connolly
 */
public class DescriptorBlackListPredicate<T extends Describable<T>> implements Predicate<Descriptor<T>> {
    protected final Set<String> blackList;

    protected DescriptorBlackListPredicate() {
        blackList = new HashSet<String>();

        URL resource = getClass().getResource(getClass().getSimpleName() + ".exclude");
        if (resource != null) {
            try {
                for (String line : com.google.common.io.Resources.readLines(resource, Charset.forName("UTF-8"))) {
                    int i = line.indexOf('#');
                    if (i != -1) {
                        line = line.substring(0, i);
                    }
                    line = line.trim();
                    if (!line.isEmpty()) {
                        blackList.add(line);
                    }
                }
            } catch (IOException e) {
                LogRecord r = new LogRecord(Level.WARNING, "Could not read {0}'s black-list");
                r.setThrown(e);
                r.setParameters(new Object[]{getClass().getSimpleName()});
                Logger.getLogger(getClass().getName()).log(r);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public final boolean apply(@Nullable Descriptor<T> input) {
        return input != null && !blackList.contains(input.clazz.getName());
    }
}
