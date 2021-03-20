package os.db.evolve;

import java.sql.SQLException;

class PostgresDatabaseTestConfig implements DatabaseTestConfig {

    public void clearDb(QueryRunner queryRunner) throws SQLException {
        queryRunner.execute("DROP SCHEMA public CASCADE;" +
                "CREATE SCHEMA public;");
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
