package metanectar.provisioning;

import metanectar.model.MasterServer;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * @author Paul Sandoz
 */
public class IdentifierFinder {

    public interface Identifier {
        int getId(MasterServer ms);
    }

    private final Comparator<MasterServer> c = new Comparator<MasterServer>() {
            public int compare(MasterServer ms1, MasterServer ms2) {
                return getId(ms1) - getId(ms2);
            }
        };

    private final Identifier identifier;

    public IdentifierFinder(Identifier identifier) {
        this.identifier = identifier;
    }

    public int getUnusedIdentifier(final List<MasterServer> l) {
        // Empty
        if (l.isEmpty())
            return 0;

        // One Element
        if (l.size() == 1) {
            final MasterServer ms = l.get(0);
            return (getId(ms) > 0) ? 0 : getId(ms) + 1;
        }

        // Multiple elements, sort by id then find a gap in the intervals
        Collections.sort(l, c);

        // If there are no gaps
        if (l.size() == getId(l.get(l.size() - 1)) + 1) {
            return l.size();
        }

        final Iterator<MasterServer> msi = l.iterator();
        MasterServer start = msi.next();
        MasterServer end = null;
        while (msi.hasNext()) {
            end = msi.next();

            if (getId(end) - getId(start) > 1) {
                return getId(start) + 1;
            }

            start = end;
        }

        return getId(end) + 1;
    }

    private int getId(MasterServer ms) {
        return identifier.getId(ms);
    }

}
