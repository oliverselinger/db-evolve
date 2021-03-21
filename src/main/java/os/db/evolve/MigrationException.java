package os.db.evolve;

import java.sql.SQLException;

public class MigrationException extends RuntimeException {

    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(Exception e) {
        super(e);
    }

    public MigrationException(String message, SQLException e) {
        super(message, e);
    }
}
