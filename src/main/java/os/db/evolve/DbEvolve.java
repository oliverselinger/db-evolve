package os.db.evolve;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DbEvolve {

    static final String DEFAULT_CLASSPATH_DIRECTORY = "sql";
    static final Pattern FILE_VERSION_PATTERN = Pattern.compile("V(\\d+)__.*");
    static final Comparator<Path> VERSION_COMPARATOR = Comparator.comparingInt(DbEvolve::extractVersionFromFileName);

    private final DataSource dataSource;
    private final String classpathDirectory;
    private final QueryRunner queryRunner;
    private final LockRepository lockRepository;
    private final SqlScriptRepository sqlScriptRepository;
    private final MessageDigest digest;
    private final Logger logger;

    public DbEvolve(DataSource dataSource) {
        this(dataSource, DEFAULT_CLASSPATH_DIRECTORY, null);
    }

    public DbEvolve(DataSource dataSource, String classpathDirectory, Logger logger) {
        if (logger == null) {
            logger = new Logger() {};
        }
        this.logger = logger;
        this.dataSource = dataSource;
        this.classpathDirectory = classpathDirectory;
        this.queryRunner = new QueryRunner(dataSource);
        this.lockRepository = new LockRepository(queryRunner, this.logger);
        this.sqlScriptRepository = new SqlScriptRepository(queryRunner, this.logger);
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new MigrationException(e);
        }

        createTablesIfNotExist();
    }

    public boolean migrate() {
        try (DbLock dbLock = new DbLock(lockRepository)) {

            boolean lock = dbLock.lock();
            if (!lock) {
                logger.log(Level.INFO, "Db-Evolve skipping migration due to locked database.");
                return false;
            }

            List<Path> sqlFiles = findAllSqlFiles();
            List<SqlScript> sqlScripts = sqlScriptRepository.findAll();

            for (Path sqlFile : sqlFiles) {

                byte[] content = Files.readAllBytes(sqlFile);
                String currentHash = hash(content);

                Optional<SqlScript> sqlScript = sqlScripts.stream()
                        .filter(script -> script.getName().equals(sqlFile.getFileName().toString()))
                        .findFirst();

                if (sqlScript.isPresent()) {
                    validateFingerprint(sqlScript.get(), currentHash);
                    continue;
                }

                migrateSqlFile(new SqlScript(sqlFile.getFileName().toString(), currentHash, LocalDateTime.now()), content);
            }
        } catch (MigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new MigrationException(e);
        }

        return true;
    }

    private void createTablesIfNotExist() {
        sqlScriptRepository.createTable();
        lockRepository.createTable();
    }

    private List<Path> findAllSqlFiles() throws URISyntaxException, IOException {
        ClassLoader classLoader = getClass().getClassLoader();

        var uri = classLoader.getResource(classpathDirectory).toURI();
        if (uri == null) {
            throw new MigrationException(String.format("No migrations found. Directory %s is not on classpath.", classpathDirectory));
        }

        Path path;
        //Check if we are in a jar file
        if (uri.getScheme().equals("jar")) {
            FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
            path = fileSystem.getPath(classpathDirectory);
        } else {
            path = Paths.get(uri);
        }

        return Files.walk(path)
                .filter(Files::isRegularFile)
                .sorted(VERSION_COMPARATOR)
                .collect(Collectors.toList());
    }

    private void validateFingerprint(SqlScript sqlScript, String currentHash) {
        // validate fingerprint
        if (!currentHash.equals(sqlScript.getHash())) {
            throw new MigrationException(String.format("Content of %s has changed", sqlScript.getName()));
        }
    }

    private void migrateSqlFile(SqlScript toMigrate, byte[] content) throws IOException, SQLException {

        try (Connection connection = dataSource.getConnection();
             Transaction transaction = new Transaction(connection);
             ByteArrayInputStream contentAsStream = new ByteArrayInputStream(content);
             InputStreamReader inReader = new InputStreamReader(contentAsStream);
             BufferedReader reader = new BufferedReader(inReader)) {

            parseAndExecuteStatements(toMigrate, connection, reader);

            sqlScriptRepository.save(connection, toMigrate);

            transaction.commit();
        }
    }

    private void parseAndExecuteStatements(SqlScript toMigrate, Connection connection, BufferedReader reader) throws IOException {
        StatementParser parser = new StatementParser();

        int lineNumber = 0;
        while (true) {
            String line = reader.readLine();
            lineNumber++;

            parser.accept(line, lineNumber);
            if (parser.isComplete()) {
                try {
                    logger.log(Level.INFO, String.format("Executing migration %s: %s", toMigrate.getName(), parser.getStatement().replace("\n", "").replace("\r", "")));
                    queryRunner.execute(connection, parser.getStatement());
                } catch (SQLException e) {
                    throw new MigrationException(String.format("%s - Invalid sql statement found at line %d", toMigrate.getName(), parser.getStartLineNumber()), e);
                }

                parser.reset();
            }

            if (line == null) {
                break;
            }
        }
    }

    private static int extractVersionFromFileName(Path path) {
        Matcher matcher = FILE_VERSION_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new MigrationException(String.format("File name %s does not meet the naming convention 'V<Version>__<Description>.sql'", path.getFileName()));
        }
        return Integer.parseInt(matcher.group(1));
    }

    private String hash(byte[] fileContent) {
        byte[] encodedhash = digest.digest(fileContent);
        return bytesToHex(encodedhash);
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

