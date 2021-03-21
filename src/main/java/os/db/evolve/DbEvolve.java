package os.db.evolve;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

    public DbEvolve(DataSource dataSource) {
        this(dataSource, DEFAULT_CLASSPATH_DIRECTORY);
    }

    public DbEvolve(DataSource dataSource, String classpathDirectory) {
        this.dataSource = dataSource;
        this.classpathDirectory = classpathDirectory;
        this.queryRunner = new QueryRunner(dataSource);
        this.lockRepository = new LockRepository(queryRunner);
        this.sqlScriptRepository = new SqlScriptRepository(queryRunner);
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new MigrationException(e);
        }
        createTablesIfNotExist();
    }

    public boolean migrate() {
        try (DbLock dbLock = new DbLock(lockRepository)){

            boolean lock = dbLock.lock();
            if (!lock) {
                System.out.println("Db-Evolve skipping migration due to locked database.");
                return false;
            }

            List<Path> sqlFiles = findAllSqlFiles();

            for (Path sqlFile : sqlFiles) {
                migrateSqlFile(sqlFile);
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

        URL resource = classLoader.getResource(classpathDirectory);
        if (resource == null) {
            throw new MigrationException(String.format("No migrations found. Directory %s is not on classpath.", classpathDirectory));
        }

        return Files.walk(Paths.get(resource.toURI()))
                .filter(Files::isRegularFile)
                .sorted(VERSION_COMPARATOR) // FIXME lexographic sorting dow not work with 1_ to 10_
                .collect(Collectors.toList());
    }

    private void migrateSqlFile(Path sqlFile) throws IOException, SQLException {
        byte[] content = Files.readAllBytes(sqlFile);
        String currentHash = hash(content);

        Optional<SqlScript> sqlScript = sqlScriptRepository.find(sqlFile.getFileName().toString());
        if (sqlScript.isPresent()) {
            // validate fingerprint
            if (!currentHash.equals(sqlScript.get().getHash())) {
                throw new MigrationException(String.format("Content of %s has changed", sqlFile));
            }

            return;
        }

        try (Connection connection = dataSource.getConnection();
             Transaction transaction = new Transaction(connection);
             ByteArrayInputStream contentAsStream = new ByteArrayInputStream(content);
             InputStreamReader inReader = new InputStreamReader(contentAsStream);
             BufferedReader reader = new BufferedReader(inReader)) {

            int lineNumber = 0;
            StringBuilder statement = new StringBuilder();

            while (true) {
                String line = reader.readLine();
                lineNumber++;

                if (line == null || line.trim().isEmpty()) {
                    if (statement.length() > 0) {
                        try {
                            queryRunner.execute(connection, statement.toString().trim());
                        } catch (SQLException e) {
                            throw new MigrationException(String.format("%s - Invalid sql statement found at line %d", sqlFile.getFileName(), lineNumber-1), e);
                        }
                        statement.setLength(0);
                    }

                    if (line == null) {
                        break;
                    }

                    if (line.trim().isEmpty()) {
                        continue;
                    }
                }

                if (line.startsWith("--")) { // skip single line comments
                    continue;
                }

                statement.append(" ").append(line);
            }

            SqlScript toSave = new SqlScript(sqlFile.getFileName().toString(), currentHash, LocalDateTime.now());
            sqlScriptRepository.save(connection, toSave);

            transaction.commit();
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

