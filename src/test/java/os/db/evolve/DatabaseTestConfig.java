package os.db.evolve;

import java.sql.SQLException;

interface DatabaseTestConfig {

    void clearDb(QueryRunner queryRunner) throws SQLException;

    String user();

    String password();

    String driver();

    String jdbcUrl();

    int maxPoolSize();
}
