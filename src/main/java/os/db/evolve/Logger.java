package os.db.evolve;

import java.util.logging.Level;

public interface Logger {

    default void log(Level level, String message) {
        if (level == Level.SEVERE) {
            System.err.println("DbEvolve: " + message);
        } else {
            System.out.println("DbEvolve: " + message);
        }

    }
}
