package metanectar.persistence;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Extension
public class SlaveLeaseTable extends DatastoreTable {
    public SlaveLeaseTable() {
        super("CREATE TABLE IF NOT EXISTS slavelease(lease VARCHAR(255) PRIMARY KEY, owner VARCHAR(255), "
                + "tenant VARCHAR(255), status INT)");
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
                    .prepareStatement("INSERT INTO slavelease (owner, lease, tenant, status) VALUES (?,?,NULL,?)");
            statement.setString(1, ownerId);
            statement.setString(2, leaseId);
            statement.setInt(3, LeaseState.REQUESTED.toStatusCode());
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
            statement = connection.prepareStatement("UPDATE slavelease SET status = ? WHERE lease = ? AND status = ?");
            statement.setInt(1, to.toStatusCode());
            statement.setString(2, leaseId);
            statement.setInt(3, from.toStatusCode());
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

    public static boolean registerLease(@NonNull String leaseId, @NonNull String tenant) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement(
                    "UPDATE slavelease SET status = ?, tenant = ? WHERE lease = ? AND status = ?");
            statement.setInt(1, LeaseState.LEASED.toStatusCode());
            statement.setString(2, tenant);
            statement.setString(3, leaseId);
            statement.setInt(4, LeaseState.AVAILABLE.toStatusCode());
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

    public static boolean returnLease(@NonNull String leaseId, @NonNull String tenant) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("UPDATE slavelease SET status = ?, tenant = NULL WHERE lease = ? AND status = ?");
            statement.setInt(1, LeaseState.RETURNED.toStatusCode());
            statement.setString(2, leaseId);
            statement.setInt(3, LeaseState.LEASED.toStatusCode());
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

    public static enum LeaseState {
        /**
         * Initial state for a lease.
         */
        REQUESTED(1) {
            /** {@inheritDoc} */
            @Override
            @NonNull
            public Set<LeaseState> validTransitions() {
                return set(PLANNED, DECOMMISSIONED);
            }
        },
        /**
         * We have got a {@link hudson.slaves.NodeProvisioner.PlannedNode}
         */
        PLANNED(25) {
            /** {@inheritDoc} */
            @Override
            @NonNull
            public Set<LeaseState> validTransitions() {
                return set(AVAILABLE, DECOMMISSIONED);
            }
        },
        /**
         * The {@link hudson.slaves.NodeProvisioner.PlannedNode} was realized.
         */
        AVAILABLE(50) {
            /** {@inheritDoc} */
            @Override
            @NonNull
            public Set<LeaseState> validTransitions() {
                return set(LEASED, DECOMMISSIONING);
            }
        },
        /**
         * The node has been leased out.
         */
        LEASED(55) {
            /** {@inheritDoc} */
            @Override
            @NonNull
            public Set<LeaseState> validTransitions() {
                return set(RETURNED);
            }
        },
        /**
         * The node has been returned from lease.
         */
        RETURNED(60) {
            /** {@inheritDoc} */
            @Override
            @NonNull
            public Set<LeaseState> validTransitions() {
                return set(AVAILABLE, DECOMMISSIONING);
            }
        },
        /**
         * The node is being decommissioned.
         */
        DECOMMISSIONING(90) {
            /** {@inheritDoc} */
            @Override
            @NonNull
            public Set<LeaseState> validTransitions() {
                return set(DECOMMISSIONED);
            }
        },
        /**
         * The node has been decommissioned.
         */
        DECOMMISSIONED(100) {
            /** {@inheritDoc} */
            @Override
            @NonNull
            public Set<LeaseState> validTransitions() {
                return Collections.emptySet();
            }
        };

        private final int statusCode;

        LeaseState(int statusCode) {
            this.statusCode = statusCode;
        }

        /**
         * Returns the set of valid states that this state can transition to.
         *
         * @return the set of valid states that this state can transition to.
         */
        @NonNull
        public abstract Set<LeaseState> validTransitions();

        @NonNull
        private static Set<LeaseState> set(LeaseState... states) {
            return new HashSet<LeaseState>(Arrays.asList(states));
        }

        public int toStatusCode() {
            return statusCode;
        }

        @CheckForNull
        public static LeaseState fromStatusCode(int statusCode) {
            for (LeaseState state : values()) {
                if (state.statusCode == statusCode) {
                    return state;
                }
            }
            return null;
        }
    }
}
