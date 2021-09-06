package os.db.evolve;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DbEvolve {

    static final String DEFAULT_CLASSPATH_DIRECTORY = "sql";
    static final Pattern FILE_VERSION_PATTERN = Pattern.compile("V(\\d+)__.*");
    static final Comparator<String> VERSION_COMPARATOR = Comparator.comparingInt(DbEvolve::extractVersionFromFileName);
    static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9-\\_]+)\\}");
    private static final MessageDigest digest;

    static {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new MigrationException("Unable to MessageDigest.getInstance of SHA-256", e);
        }
    }
    private final DataSource dataSource;
    private final String classpathDirectory;
    private final Logger logger;

    public DbEvolve(DataSource dataSource) {
        this(dataSource, DEFAULT_CLASSPATH_DIRECTORY, Logger.NO_OP);
    }

    public DbEvolve(DataSource dataSource, String classpathDirectory, Logger logger) {
        this.logger = logger != null ? logger : Logger.NO_OP;
        this.dataSource = dataSource;
        this.classpathDirectory = classpathDirectory;

        createTablesIfNotExist();
    }

    public boolean migrate() throws IOException, URISyntaxException, SQLException {
        return migrate(Collections.emptyMap());
    }

    public boolean migrate(Map<String, String> placeholderValues) throws IOException, URISyntaxException, SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);

            if (!lock(connection)) {
                logger.log(Logger.Level.INFO, "Db-Evolve skipping migration due to locked database.");
                return false;
            }

            try {
                SortedSet<String> filenames = readFilenamesFromClasspath(classpathDirectory);
                Map<String, String> sqlScriptsByName = selectAllFromDb(connection);

                for (String filename : filenames) {
                    Path sqlFile = Paths.get(getResource(classpathDirectory + "/" + filename).toURI());
                    byte[] content = Files.readAllBytes(sqlFile);
                    String hash = hash(content);

                    String fileName = sqlFile.getFileName().toString();
                    String knownHash = sqlScriptsByName.get(fileName);

                    if (knownHash != null) {
                        if (!hash.equals(knownHash)) {
                            throw new MigrationException(String.format("Content of %s has changed. Expected hash %s but was %s", fileName, knownHash, hash));
                        }
                        continue;
                    }

                    migrateSqlFile(connection, fileName, hash, content, placeholderValues);
                }
            } finally {
                unlock(connection);
            }
        }

        return true;
    }

    public static String hash(Path sqlFile) throws IOException {
        byte[] content = Files.readAllBytes(sqlFile);
        return hash(content);
    }

    private void createTablesIfNotExist() {
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, "CREATE TABLE DB_EVOLVE (name VARCHAR(255) NOT NULL, hash VARCHAR(64) NOT NULL, timestamp TIMESTAMP, PRIMARY KEY (name))");
            execute(connection, "CREATE TABLE DB_EVOLVE_LOCK (DB_LOCK INTEGER, TIMESTAMP TIMESTAMP, PRIMARY KEY (DB_LOCK))");
            execute(connection, "INSERT INTO DB_EVOLVE_LOCK (DB_LOCK) VALUES (0)");
        } catch (SQLException throwables) {
            // ignore => assumption table already exist. If not migration will fail anyway.
            logger.log(Logger.Level.DEBUG, throwables.getMessage());
        }
    }

    boolean lock(Connection connection) throws SQLException {
        int count = executeUpdate(connection, "UPDATE DB_EVOLVE_LOCK SET DB_LOCK = 1, TIMESTAMP = ? WHERE DB_LOCK = 0", Timestamp.valueOf(LocalDateTime.now()));
        return count == 1;
    }

    boolean unlock(Connection connection) throws SQLException {
        int count = executeUpdate(connection, "UPDATE DB_EVOLVE_LOCK SET DB_LOCK = 0 WHERE DB_LOCK = 1");
        return count == 1;
    }

    private void migrateSqlFile(Connection connection, String fileName, String hash, byte[] content, Map<String, String> placeholderValues) throws IOException, SQLException {
        connection.setAutoCommit(false);

        try (ByteArrayInputStream contentAsStream = new ByteArrayInputStream(content);
             InputStreamReader inReader = new InputStreamReader(contentAsStream);
             BufferedReader reader = new BufferedReader(inReader)) {

            parseAndExecuteStatements(fileName, reader, stmt -> executeMigration(connection, fileName, stmt, placeholderValues));

            executeUpdate(connection, "INSERT INTO DB_EVOLVE (NAME, HASH, TIMESTAMP) VALUES (?, ?, ?)", fileName, hash, Timestamp.valueOf(LocalDateTime.now()));
        } catch (java.lang.Exception ex) {
            connection.rollback();
            connection.setAutoCommit(true);
            throw ex;
        }
        connection.commit();
        connection.setAutoCommit(true);
    }

    void parseAndExecuteStatements(String fileName, BufferedReader reader, StatementExecutor statementExecutor) throws IOException {
        int lineNumber = 0;
        String line;
        StringBuilder statement = new StringBuilder();
        int statementStartLineNumber = -1;
        String delimiter = ";";

        try {
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.startsWith("--") || line.isBlank()) { // skip single line comments and blank lines
                    continue;
                }

                if (line.startsWith("DELIMITER ")) {
                    delimiter = line.substring(10);
                    continue;
                }

                if (statementStartLineNumber == -1) {
                    statementStartLineNumber = lineNumber;
                }

                statement.append(line).append("\n");

                if (line.endsWith(delimiter)) {
                    int length = statement.length();
                    statement.replace(length - delimiter.length() - 1, length, ""); // remove delimiter
                    statementExecutor.execute(statement.toString());

                    // reset
                    statement.setLength(0);
                    statementStartLineNumber = -1;
                    delimiter = ";";
                }
            }
        } catch (SQLException e) {
            throw new MigrationException(String.format("%s - Invalid sql statement found at line %d", fileName, statementStartLineNumber), e);
        }
    }

    private void executeMigration(Connection connection, String fileName, String statement, Map<String, String> placeHolderValues) throws SQLException {
        statement = replacePlaceholder(statement, placeHolderValues);
        logger.log(Logger.Level.INFO, String.format("Executing migration %s:\n%s", fileName, statement));
        execute(connection, statement);
    }

    String replacePlaceholder(String statement, Map<String, String> placeHolderValues) {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = DbEvolve.PLACEHOLDER_PATTERN.matcher(statement);
        while(matcher.find()){
            String placeholder = matcher.group(1);
            String replacement = placeHolderValues.get(placeholder);
            if (replacement == null) {
                throw new MigrationException(String.format("Missing value for placeholder '%s'", placeholder));
            }
            matcher.appendReplacement(builder, replacement);
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private SortedSet<String> readFilenamesFromClasspath(String path) throws IOException {
        SortedSet<String> filenames = new TreeSet<>(VERSION_COMPARATOR);
        try (InputStream in = getResourceAsStream(path)) {
            if (in == null) {
                throw new MigrationException(String.format("Directory %s not found on classpath", classpathDirectory));
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                String resource;
                while ((resource = br.readLine()) != null) {
                    filenames.add(resource);
                }
            }
        }
        return filenames;
    }

    private InputStream getResourceAsStream(String resource) {
        final InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
        return in == null ? getClass().getResourceAsStream(resource) : in;
    }

    private URL getResource(String resource) {
        final URL url = getClass().getClassLoader().getResource(resource);
        return url == null ? getClass().getResource(resource) : url;
    }

    private static int extractVersionFromFileName(String filename) {
        Matcher matcher = FILE_VERSION_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            throw new MigrationException(String.format("File name %s does not meet the naming convention 'V<Version>__<Description>.sql'", filename));
        }
        return Integer.parseInt(matcher.group(1));
    }

    Map<String, String> selectAllFromDb(Connection connection) throws SQLException {
        try (Statement ps = connection.createStatement()) {
            ResultSet rs = ps.executeQuery("SELECT * FROM DB_EVOLVE ORDER BY TIMESTAMP");

            Map<String, String> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getString("NAME"), rs.getString("HASH"));
            }
            return result;
        }
    }

    private static String hash(byte[] fileContent) {
        byte[] encodedhash = digest.digest(fileContent);
        return bytesToHex(encodedhash);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    void execute(Connection connection, String sqlStatement) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sqlStatement);
        }
    }

    int executeUpdate(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int cnt = 0;
            for (Object param : params) {
                ps.setObject(++cnt, param);
            }
            return ps.executeUpdate();
        }
    }

    public interface Logger {

        Logger NO_OP = new Logger() {
        };

        default void log(Level level, String message) {
            System.out.println("DbEvolve: " + message);
        }

        enum Level {
            DEBUG, INFO
        }
    }

    public static class MigrationException extends RuntimeException {

        public MigrationException(String message) {
            super(message);
        }

        public MigrationException(String message, Exception e) {
            super(message, e);
        }
    }

    public interface StatementExecutor {
        void execute(String statement) throws SQLException;
    }
}