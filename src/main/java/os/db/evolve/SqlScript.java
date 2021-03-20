package os.db.evolve;

import java.time.LocalDateTime;

class SqlScript {

    private final String name;
    private final String hash;
    private final LocalDateTime timestamp;

    public SqlScript(String name, String hash, LocalDateTime timestamp) {
        this.name = name;
        this.hash = hash;
        this.timestamp = timestamp;
    }

    public String getName() {
        return name;
    }

    public String getHash() {
        return hash;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
