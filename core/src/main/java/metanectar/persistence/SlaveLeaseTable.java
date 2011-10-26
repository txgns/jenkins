package metanectar.persistence;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import org.apache.commons.io.IOUtils;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static metanectar.persistence.TableSchema._blob;
import static metanectar.persistence.TableSchema._int;
import static metanectar.persistence.TableSchema._string;
import static metanectar.persistence.TableSchema._tstamp;

@Extension
public class SlaveLeaseTable extends DatastoreTable<String> {

    static final String LEASE_COLUMN = "lease";
    static final String OWNER_COLUMN = "owner";
    static final String TENANT_COLUMN = "tenant";
    static final String STATUS_COLUMN = "status";
    static final String LASTMOD_COLUMN = "lastmod";
    static final String RESOURCE_COLUMN = "resource";

    public SlaveLeaseTable() {
        super(new TableSchema<String>("slavelease",
                _string(LEASE_COLUMN).primaryKey(),
                _string(OWNER_COLUMN),
                _string(TENANT_COLUMN),
                _int(STATUS_COLUMN),
                _blob(RESOURCE_COLUMN),
                _tstamp(LASTMOD_COLUMN))
                .withTrigger(SlaveLeaseTrigger.class));
    }

    public static void dropOwner(@NonNull String ownerId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;

        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("DELETE FROM slavelease WHERE owner = ?");
            statement.setString(1, ownerId);
            statement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            // ignore
        } finally {
            close(statement);
            close(connection);
        }
    }

    public static boolean registerRequest(@NonNull String ownerId, String leaseId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection
                    .prepareStatement("INSERT INTO slavelease (owner, lease, tenant, status, lastmod) "
                            + "VALUES (?,?,NULL,?,?)");
            statement.setString(1, ownerId);
            statement.setString(2, leaseId);
            statement.setInt(3, LeaseState.REQUESTED.toStatusCode());
            statement.setTimestamp(4, currentTimestamp());
            boolean result = statement.executeUpdate() == 1;
            connection.commit();
            return result;
        } catch (SQLException e) {
            return false;
        } finally {
            close(statement);
            close(connection);
        }
    }

    public static boolean decommissionLease(String leaseId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection
                    .prepareStatement("DELETE FROM slavelease WHERE lease = ? and status = ?");
            statement.setString(1, leaseId);
            statement.setInt(2, LeaseState.DECOMMISSIONED.toStatusCode());
            boolean result = statement.executeUpdate() == 1;
            connection.commit();
            return result;
        } catch (SQLException e) {
            return false;
        } finally {
            close(statement);
            close(connection);
        }
    }

    public static boolean updateState(@NonNull String leaseId, @NonNull LeaseState from, @NonNull LeaseState to) {
        if (!from.validTransitions().contains(to)) {
            throw new IllegalStateException("Transition from " + from + " to " + to + " is not allowed");
        }
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("UPDATE slavelease SET status = ?, lastmod = ? "
                    + "WHERE lease = ? AND status = ?");
            statement.setInt(1, to.toStatusCode());
            statement.setTimestamp(2, currentTimestamp());
            statement.setString(3, leaseId);
            statement.setInt(4, from.toStatusCode());
            boolean result = statement.executeUpdate() == 1;
            connection.commit();
            return result;
        } catch (SQLException e) {
            return false;
        } finally {
            close(statement);
            close(connection);
        }
    }

    public static boolean updateStateAndResource(@NonNull String leaseId, @NonNull LeaseState from,
                                                 @NonNull LeaseState to, @Nullable byte[] resource) {
        if (!from.validTransitions().contains(to)) {
            throw new IllegalStateException("Transition from " + from + " to " + to + " is not allowed");
        }
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement(
                    "UPDATE slavelease SET status = ?, resource = ?, lastmod = ? "
                            + "WHERE lease = ? AND status = ? AND resource IS NOT NULL");
            statement.setInt(1, to.toStatusCode());
            statement.setBlob(2, resource == null ? null : new ByteArrayInputStream(resource));
            statement.setTimestamp(3, currentTimestamp());
            statement.setString(4, leaseId);
            statement.setInt(5, from.toStatusCode());
            boolean result = statement.executeUpdate() == 1;
            connection.commit();
            return result;
        } catch (SQLException e) {
            return false;
        } finally {
            close(statement);
            close(connection);
        }
    }

    public static boolean planResource(@NonNull String leaseId, @Nullable byte[] resource) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement(
                    "UPDATE slavelease SET status = ?, resource = ?, lastmod = ? "
                            + "WHERE lease = ? AND status = ? AND resource IS NULL");
            statement.setInt(1, LeaseState.PLANNED.toStatusCode());
            statement.setBlob(2, resource == null ? null : new ByteArrayInputStream(resource));
            statement.setTimestamp(3, currentTimestamp());
            statement.setString(4, leaseId);
            statement.setInt(5, LeaseState.REQUESTED.toStatusCode());
            boolean result = statement.executeUpdate() == 1;
            connection.commit();
            return result;
        } catch (SQLException e) {
            return false;
        } finally {
            close(statement);
            close(connection);
        }
    }

    public static boolean setResource(@NonNull String leaseId, @Nullable byte[] resource) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement(
                    "UPDATE slavelease SET resource = ?, lastmod = ? WHERE lease = ? AND resource IS NULL");
            statement.setBlob(1, resource == null ? null : new ByteArrayInputStream(resource));
            statement.setTimestamp(2, currentTimestamp());
            statement.setString(3, leaseId);
            boolean result = statement.executeUpdate() == 1;
            connection.commit();
            return result;
        } catch (SQLException e) {
            return false;
        } finally {
            close(statement);
            close(connection);
        }
    }

    public static boolean clearResource(@NonNull String leaseId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement(
                    "UPDATE slavelease SET resource = NULL, lastmod = ? WHERE lease = ?");
            statement.setTimestamp(1, currentTimestamp());
            statement.setString(2, leaseId);
            boolean result = statement.executeUpdate() == 1;
            connection.commit();
            return result;
        } catch (SQLException e) {
            return false;
        } finally {
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static byte[] getResource(@NonNull String leaseId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT resource FROM slavelease WHERE lease = ?");
            statement.setString(1, leaseId);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                Blob blob = resultSet.getBlob(1);
                if (blob == null) {
                    return null;
                }
                return IOUtils.toByteArray(blob.getBinaryStream());
            }
            return null;
        } catch (SQLException e) {
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    public static boolean registerLease(@NonNull String leaseId, @NonNull String tenant) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement(
                    "UPDATE slavelease SET status = ?, tenant = ?, lastmod = ? WHERE lease = ? AND status = ?");
            statement.setInt(1, LeaseState.LEASED.toStatusCode());
            statement.setString(2, tenant);
            statement.setTimestamp(3, currentTimestamp());
            statement.setString(4, leaseId);
            statement.setInt(5, LeaseState.AVAILABLE.toStatusCode());
            boolean result = statement.executeUpdate() == 1;
            connection.commit();
            return result;
        } catch (SQLException e) {
            return false;
        } finally {
            close(statement);
            close(connection);
        }
    }

    public static boolean returnLease(@NonNull String leaseId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection
                    .prepareStatement("UPDATE slavelease SET status = ?, tenant = NULL, lastmod = ? "
                            + "WHERE lease = ? AND status = ?");
            statement.setInt(1, LeaseState.RETURNED.toStatusCode());
            statement.setTimestamp(2, currentTimestamp());
            statement.setString(3, leaseId);
            statement.setInt(4, LeaseState.LEASED.toStatusCode());
            boolean result = statement.executeUpdate() == 1;
            connection.commit();
            return result;
        } catch (SQLException e) {
            return false;
        } finally {
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static Set<String> getOwners() {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT DISTINCT owner FROM slavelease");
            Set<String> result = new HashSet<String>();
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(resultSet.getString("owner"));
            }
            return Collections.unmodifiableSet(result);
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static Set<String> getLeases() {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT lease FROM slavelease");
            Set<String> result = new HashSet<String>();
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(resultSet.getString("lease"));
            }
            return Collections.unmodifiableSet(result);
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static Set<String> getLeases(LeaseState status) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT lease FROM slavelease WHERE status = ?");
            statement.setInt(1, status.toStatusCode());
            Set<String> result = new HashSet<String>();
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(resultSet.getString("lease"));
            }
            return Collections.unmodifiableSet(result);
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static Set<String> getLeases(String ownerId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT lease FROM slavelease WHERE owner = ?");
            statement.setString(1, ownerId);
            Set<String> result = new HashSet<String>();
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(resultSet.getString("lease"));
            }
            return Collections.unmodifiableSet(result);
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static Set<String> getLeases(String ownerId, LeaseState status) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT lease FROM slavelease WHERE owner = ? AND status = ?");
            statement.setString(1, ownerId);
            statement.setInt(2, status.toStatusCode());
            Set<String> result = new HashSet<String>();
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(resultSet.getString("lease"));
            }
            return Collections.unmodifiableSet(result);
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static Set<String> getTenantLeases(String tenant) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT lease FROM slavelease WHERE tenant = ? AND status = ?");
            statement.setString(1, tenant);
            statement.setInt(2, LeaseState.LEASED.toStatusCode());
            Set<String> result = new HashSet<String>();
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(resultSet.getString("lease"));
            }
            return Collections.unmodifiableSet(result);
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static String getOwner(String leaseId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT owner FROM slavelease WHERE lease = ?");
            statement.setString(1, leaseId);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("owner");
            }
            return null;
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static LeaseState getStatus(String leaseId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT status FROM slavelease WHERE lease = ?");
            statement.setString(1, leaseId);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return LeaseState.fromStatusCode(resultSet.getInt("status"));
            }
            return null;
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static String getTenant(String leaseId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT tenant FROM slavelease WHERE lease = ? and status = ?");
            statement.setString(1, leaseId);
            statement.setInt(2, LeaseState.LEASED.toStatusCode());
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("tenant");
            }
            return null;
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }

    }

    @CheckForNull
    public static LeaseRecord getLeaseRecord(String leaseId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT lease, owner, status, tenant, lastmod "
                    + "FROM slavelease WHERE lease = ?");
            statement.setString(1, leaseId);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return toLeaseRecord(resultSet);
            }
            return null;
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static Set<LeaseRecord> getLeaseRecords() {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT lease, owner, status, tenant, lastmod "
                    + "FROM slavelease");
            Set<LeaseRecord> result = new HashSet<LeaseRecord>();
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(toLeaseRecord(resultSet));
            }
            return Collections.unmodifiableSet(result);
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static Set<LeaseRecord> getLeaseRecords(LeaseState status) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT lease, owner, status, tenant, lastmod "
                    + "FROM slavelease WHERE status = ?");
            statement.setInt(1, status.toStatusCode());
            Set<LeaseRecord> result = new HashSet<LeaseRecord>();
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(toLeaseRecord(resultSet));
            }
            return Collections.unmodifiableSet(result);
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static Set<LeaseRecord> getLeaseRecords(String ownerId) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT lease, owner, status, tenant, lastmod "
                    + "FROM slavelease WHERE owner = ?");
            statement.setString(1, ownerId);
            Set<LeaseRecord> result = new HashSet<LeaseRecord>();
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(toLeaseRecord(resultSet));
            }
            return Collections.unmodifiableSet(result);
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static Set<LeaseRecord> getLeaseRecords(String ownerId, LeaseState status) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT lease, owner, status, tenant, lastmod "
                    + "FROM slavelease WHERE owner = ? AND status = ?");
            statement.setString(1, ownerId);
            statement.setInt(2, status.toStatusCode());
            Set<LeaseRecord> result = new HashSet<LeaseRecord>();
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(toLeaseRecord(resultSet));
            }
            return Collections.unmodifiableSet(result);
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    @CheckForNull
    public static Set<LeaseRecord> getTenantLeaseRecords(String tenant) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("SELECT lease, owner, status, tenant, lastmod "
                    + "FROM slavelease WHERE tenant = ? AND status = ?");
            statement.setString(1, tenant);
            statement.setInt(2, LeaseState.LEASED.toStatusCode());
            Set<LeaseRecord> result = new HashSet<LeaseRecord>();
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(toLeaseRecord(resultSet));
            }
            return Collections.unmodifiableSet(result);
        } catch (SQLException e) {
            return null;
        } finally {
            close(resultSet);
            close(statement);
            close(connection);
        }
    }

    private static LeaseRecord toLeaseRecord(ResultSet row) throws SQLException {
        String leaseId = row.getString(LEASE_COLUMN);
        if (leaseId == null) {
            return null;
        }
        LeaseState status = LeaseState.fromStatusCode(row.getInt(STATUS_COLUMN));
        if (status == null) {
            return null;
        }
        String ownerId = row.getString(OWNER_COLUMN);
        String tenantId = row.getString(TENANT_COLUMN);
        Timestamp lastMod = row.getTimestamp(LASTMOD_COLUMN);

        return new LeaseRecord(leaseId, ownerId, tenantId, status, lastMod);
    }

    private static Timestamp currentTimestamp() {
        return new Timestamp(Calendar.getInstance().getTime().getTime());
    }

}
