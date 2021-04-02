package os.db.evolve;


public interface Logger {

    default void log(Level level, String message) {
        if (level == Level.ERROR) {
            System.err.println("DbEvolve: " + message);
        } else {
            System.out.println("DbEvolve: " + message);
        }

    }
    enum Level {
        DEBUG, INFO, ERROR
    }
}
