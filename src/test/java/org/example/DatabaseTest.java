package org.example;

import org.junit.ClassRule;
import org.junit.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.util.StringColumnMapper;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class DatabaseTest {

    @ClassRule
    public static DatabaseRule db = new DatabaseRule()
            .withInitSql(
                    "CREATE USER example WITH PASSWORD 'secret'",
                    "CREATE DATABASE db",
                    "GRANT ALL PRIVILEGES ON DATABASE db TO example");

    @Test
    public void shouldConnectToDatabase() throws InterruptedException, SQLException {
        DataSource dataSource = getDataSource();
        DBI dbi = new DBI(dataSource);
        try (Handle h = dbi.open()) {
            h.execute("create table something (id int primary key, name varchar(100))");
            h.execute("insert into something (id, name) values (?, ?)", 1, "Brian");
            String name = h.createQuery("select name from something where id = :id")
                    .bind("id", 1).map(StringColumnMapper.INSTANCE).first();
            assertThat(name, equalTo("Brian"));
        }
    }

    private static DataSource getDataSource() throws SQLException {
        String url = "jdbc:postgresql://localhost:" + db.port() + "/db";
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser("example");
        dataSource.setPassword("secret");
        dataSource.setConnectTimeout(5);
        dataSource.setSocketTimeout(5);
        return dataSource;
    }
}
