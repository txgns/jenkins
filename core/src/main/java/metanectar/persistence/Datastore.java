package metanectar.persistence;

import hudson.model.Hudson;
import org.h2.jdbcx.JdbcConnectionPool;
import org.kohsuke.stapler.Stapler;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.sql.DataSource;
import java.io.File;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 */
public class Datastore implements ServletContextListener {

    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    public static DataSource getDataSource() {
        ServletContext servletContext = Hudson.getInstance().servletContext;
        LOCK.readLock().lock();
        try {
            // in general we should be on this fast path always
            Object connectionPool = servletContext.getAttribute(Datastore.class.getName());
            if (connectionPool instanceof JdbcConnectionPool) {
                return (DataSource) connectionPool;
            }
        } finally {
            LOCK.readLock().unlock();
        }
        LOCK.writeLock().lock();
        try {
            // check nobody else created it while we were waiting on the write lock
            Object connectionPool = servletContext.getAttribute(Datastore.class.getName());
            if (connectionPool instanceof JdbcConnectionPool) {
                return (DataSource) connectionPool;
            }
            File rootDir = Hudson.getInstance().getRootDir();
            String jdbcUrl = "jdbc:h2:" + new File(rootDir, "run-time-state").getAbsolutePath();
            JdbcConnectionPool pool = JdbcConnectionPool.create(jdbcUrl, "sa", "sa");
            for (DatastoreTable table: Hudson.getInstance().getExtensionList(DatastoreTable.class)) {
                table.createIfMissing(pool);
            }
            servletContext.setAttribute(Datastore.class.getName(), pool);
            return pool;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public void contextInitialized(ServletContextEvent sce) {
    }

    public void contextDestroyed(ServletContextEvent sce) {
        // assuming the connection pool has been created, tear it down again
        Object connectionPool;
        LOCK.writeLock().lock();
        try {
            connectionPool = sce.getServletContext().getAttribute(Datastore.class.getName());
            sce.getServletContext().removeAttribute(Datastore.class.getName());
        } finally {
            LOCK.writeLock().unlock();
        }
        if (connectionPool instanceof JdbcConnectionPool) {
            JdbcConnectionPool jdbcConnectionPool = (JdbcConnectionPool) connectionPool;
            jdbcConnectionPool.dispose();
        }
    }
}
