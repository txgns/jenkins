package metanectar.persistence;

import org.h2.tools.TriggerAdapter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Callback from the DB to notify when there were row changes in the {@link SlaveLeaseTable}.
 */
public class SlaveLeaseTrigger extends TriggerAdapter {
    @Override
    public void fire(Connection conn, ResultSet oldRow, ResultSet newRow) throws SQLException {
        String oldLease = null;
        if (oldRow != null) {
            oldLease = oldRow.getString(SlaveLeaseTable.LEASE_COLUMN);
            if (oldLease != null) {
                SlaveLeaseListener.notifyChanged(oldLease);
            }
        }
        if (newRow != null) {
            String newLease = newRow.getString(SlaveLeaseTable.LEASE_COLUMN);
            if (newLease != null && !newLease.equals(oldLease)) {
                SlaveLeaseListener.notifyChanged(newLease);
            }
        }
    }
}
