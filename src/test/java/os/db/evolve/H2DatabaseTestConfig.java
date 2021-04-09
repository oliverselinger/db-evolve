package os.db.evolve;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

class H2DatabaseTestConfig implements DatabaseTestConfig {

    public void clearDb(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        }
    }

    public String user() {
        return "sa";
    }

    public String password() {
        return "";
    }

    public String driver() {
        return "org.h2.Driver";
    }

    public String jdbcUrl() {
        return "jdbc:h2:mem:dbevolve";
    }

    public int maxPoolSize() {
        return 1;
    }
}
