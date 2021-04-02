package os.db.evolve;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

class LockRepository {

    private final QueryRunner queryRunner;

    LockRepository(QueryRunner queryRunner) {
        this.queryRunner = queryRunner;
    }

    void createTable() {
        try {
            queryRunner.execute("CREATE TABLE DB_EVOLVE_LOCK (DB_LOCK INTEGER, TIMESTAMP TIMESTAMP, PRIMARY KEY (DB_LOCK));");
            queryRunner.executeUpdate("INSERT INTO DB_EVOLVE_LOCK (DB_LOCK,TIMESTAMP) VALUES (0, ?)", Timestamp.valueOf(LocalDateTime.now()));
        } catch (SQLException throwables) {
            // ignore => assumption table already exist. If not migration will fail anyway.
        }
    }

    boolean lock() throws SQLException {
        String updateStmt = "" +
                "UPDATE DB_EVOLVE_LOCK" +
                " SET DB_LOCK = 1, TIMESTAMP = ?" +
                " WHERE DB_LOCK = 0";

        int count = queryRunner.executeUpdate(updateStmt, Timestamp.valueOf(LocalDateTime.now()));
        return count == 1;
    }

    boolean unlock() throws SQLException {
        String updateStmt = "" +
                "UPDATE DB_EVOLVE_LOCK" +
                " SET DB_LOCK = 0" +
                " WHERE DB_LOCK = 1";

        int count = queryRunner.executeUpdate(updateStmt);
        return count == 1;
    }

}
