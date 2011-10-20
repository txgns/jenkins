package metanectar.persistence;

import hudson.Extension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

@Extension
public class UIDTable extends DatastoreTable {
    public UIDTable() {
        super("CREATE TABLE IF NOT EXISTS uids(uid VARCHAR(255) PRIMARY KEY)");
    }

    public static String generate() {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("INSERT INTO uids(uid) VALUES (?)");
            while (true) {
                String id = UUID.randomUUID().toString();
                try {
                    statement.setString(1, id);
                    statement.executeUpdate();
                    connection.commit();
                    return id;
                } catch (SQLException e) {
                    // ignore
                }
            }
        } catch (SQLException e) {
            // ignore
        } finally {
            close(statement);
            close(connection);
        }
        return null;
    }

    public static void drop(String uid) {
        DataSource dataSource = Datastore.getDataSource();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement("DELETE FROM uids WHERE uid = ?");
            statement.setString(1, uid);
            statement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            // ignore
        } finally {
            close(statement);
            close(connection);
        }
    }
}
