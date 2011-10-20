package metanectar.persistence;

import metanectar.test.MetaNectarRule;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class UIDTableTest {

    @ClassRule
    public static MetaNectarRule m = new MetaNectarRule();

    @Test
    public void doesNotGenerateNulls() throws Exception {
        assertThat(UIDTable.generate(), notNullValue());
    }

    @Test
    public void generatesUnique() throws Exception {
        assertThat(UIDTable.generate(), not(is(UIDTable.generate())));
    }
}
