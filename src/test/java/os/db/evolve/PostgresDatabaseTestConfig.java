package os.db.evolve;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

class PostgresDatabaseTestConfig implements DatabaseTestConfig {

    public void clearDb(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE;" +
                    "CREATE SCHEMA public;");
        }
    }

    public String user() {
        return "dbevolve";
    }

    public String password() {
        return "dbevolve";
    }

    public String driver() {
        return "org.postgresql.Driver";
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://127.0.0.1:5432/dbevolve";
    }

    public int maxPoolSize() {
        return 5;
    }
}
