package os.db.evolve;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

class MySqlDatabaseTestConfig implements DatabaseTestConfig {

    public void clearDb(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS TEST1;");
            statement.execute("DROP TABLE IF EXISTS TEST2;");
            statement.execute("DROP TABLE IF EXISTS DB_EVOLVE_LOCK;");
            statement.execute("DROP TABLE IF EXISTS DB_EVOLVE;");
        }
    }

    public String user() {
        return "dbevolve";
    }

    public String password() {
        return "dbevolve";
    }

    public String driver() {
        return "com.mysql.cj.jdbc.Driver";
    }

    public String jdbcUrl() {
        return "jdbc:mysql://localhost:3306/dbevolve";
    }

    public int maxPoolSize() {
        return 5;
    }
}
