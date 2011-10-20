package metanectar.persistence;

import hudson.ExtensionPoint;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An extension point for tables in the datastore that allows the table schema to be automatically created on first
 * load.
 */
public abstract class DatastoreTable implements ExtensionPoint {

    private static final Logger LOGGER = Logger.getLogger(DatastoreTable.class.getName());

    private final String ddlSql;

    protected DatastoreTable(String ddlSql) {
        this.ddlSql = ddlSql;
    }

    public final void createIfMissing(DataSource dataSource) {
        Connection connection = null;
        Statement statement = null;
        try {
            LOGGER.log(Level.FINE, "Creating table {0}", ddlSql);
            connection = dataSource.getConnection();
            statement = connection.createStatement();
            for (String ddl : ddlSql.split(";")) {
                LOGGER.log(Level.FINE, "SQL> {0}", ddl);
                statement.executeUpdate(ddl);
            }
            LOGGER.log(Level.INFO, "Table created: {0}", ddlSql);
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Could not create table (may exist already)", e);
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    LOGGER.log(Level.FINE, "Could not close statement", e);
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    LOGGER.log(Level.FINE, "Could not close connection", e);
                }
            }
        }
    }
    public static void close(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    public static void close(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    public static void close(ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }
}
