package os.db.evolve;

public class MigrationException extends RuntimeException {

    public MigrationException(String message) {
        super(message);
    }
}
