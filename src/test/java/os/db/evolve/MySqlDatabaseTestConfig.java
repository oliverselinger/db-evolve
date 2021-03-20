package os.db.evolve;

import java.sql.SQLException;

class MySqlDatabaseTestConfig implements DatabaseTestConfig {

    public void clearDb(QueryRunner queryRunner) throws SQLException {
        queryRunner.execute("DROP DATABASE dbevolve;");
        queryRunner.execute("CREATE DATABASE dbevolve;");
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
