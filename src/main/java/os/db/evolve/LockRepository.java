package os.db.evolve;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

class LockRepository {

    private final QueryRunner queryRunner;

    LockRepository(QueryRunner queryRunner) {
        this.queryRunner = queryRunner;
    }

    boolean lock() throws SQLException {
        String updateStmt = "" +
                "UPDATE DB_EVOLVE_LOCK" +
                " SET LOCK = TRUE, TIMESTAMP = ?" +
                " WHERE LOCK = FALSE";

        int count = queryRunner.executeUpdate(updateStmt, Timestamp.valueOf(LocalDateTime.now()));
        return count == 1;
    }

    boolean unlock() throws SQLException {
        String updateStmt = "" +
                "UPDATE DB_EVOLVE_LOCK" +
                " SET LOCK = FALSE" +
                " WHERE LOCK = TRUE";

        int count = queryRunner.executeUpdate(updateStmt);
        return count == 1;
    }

}
