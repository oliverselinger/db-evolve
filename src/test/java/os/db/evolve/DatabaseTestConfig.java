package os.db.evolve;

import javax.sql.DataSource;
import java.sql.SQLException;

interface DatabaseTestConfig {

    void clearDb(DataSource dataSource) throws SQLException;

    String user();

    String password();

    String driver();

    String jdbcUrl();

    int maxPoolSize();
}
