package os.db.evolve;

import java.sql.SQLException;

class H2DatabaseTestConfig implements DatabaseTestConfig {

    public void clearDb(QueryRunner queryRunner) throws SQLException {
        queryRunner.execute("DROP ALL OBJECTS");
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
