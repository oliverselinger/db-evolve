package os.db.evolve;

import java.sql.SQLException;

public class DbLock implements AutoCloseable {

    private final LockRepository lockRepository;
    private boolean locked;

    public DbLock(LockRepository lockRepository) {
        this.lockRepository = lockRepository;
    }

    public boolean lock() throws SQLException {
        return (locked = lockRepository.lock());
    }

    @Override
    public void close() throws SQLException {
        if (locked) {
            lockRepository.unlock();
        }
    }
}
