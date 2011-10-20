package metanectar.persistence;

import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.fail;

/**
 * Created by IntelliJ IDEA.
 * User: stephenc
 * Date: 13/10/2011
 * Time: 16:22
 * To change this template use File | Settings | File Templates.
 */
public class DatastoreTest {

    private JdbcConnectionPool pool;

    @Before
    public void setUp() throws Exception {
        pool = JdbcConnectionPool.create("jdbc:h2:mem:", "sa", "sa");
    }

    @After
    public void tearDown() throws Exception {
        if (pool != null) {
            pool.dispose();
        }
    }

    @Test
    public void doubleInsert() throws Exception {
        Connection c = null;
        Statement s = null;
        try {
            c = pool.getConnection();
            s = c.createStatement();
            s.execute("CREATE TABLE foo(bar VARCHAR(255) PRIMARY KEY)");
            s.execute("INSERT INTO foo SET bar = 'manchu'");
            try {
                s.execute("INSERT INTO foo SET bar = 'manchu'");
                fail("Second insert should fail");
            } catch (SQLException e) {
                // ignore
            }
        } finally {
            DatastoreTable.close(s);
            DatastoreTable.close(c);
        }
    }
}
