package os.db.evolve;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DbEvolve {

    private static final String DEFAULT_CLASSPATH_DIRECTORY = "sql";
    private static final String END_DELIMITER = ";";

    private final DataSource dataSource;
    private final String classpathDirectory;
    private final QueryRunner queryRunner;
    private final SqlScriptRepository sqlScriptRepository;
    private final MessageDigest digest;

    public DbEvolve(DataSource dataSource) throws SQLException, NoSuchAlgorithmException {
        this(dataSource, DEFAULT_CLASSPATH_DIRECTORY);
    }

    public DbEvolve(DataSource dataSource, String classpathDirectory) throws SQLException, NoSuchAlgorithmException {
        this.dataSource = dataSource;
        this.classpathDirectory = classpathDirectory;
        this.queryRunner = new QueryRunner(dataSource);
        this.sqlScriptRepository = new SqlScriptRepository(queryRunner);
        this.digest = MessageDigest.getInstance("SHA-256");
        createTableIfNotExists();
    }

    public void migrate() throws IOException, URISyntaxException, SQLException {
        List<Path> sqlFiles = findAllSqlFiles();

        for (Path sqlFile : sqlFiles) {
            migrateSqlFile(sqlFile);
        }
    }

    private void createTableIfNotExists() throws SQLException { // does not work with Oracle
        queryRunner.execute("CREATE TABLE IF NOT EXISTS DB_EVOLVE (name VARCHAR(255) NOT NULL, hash VARCHAR(64) NOT NULL, timestamp TIMESTAMP, PRIMARY KEY (name));");
    }

    private List<Path> findAllSqlFiles() throws URISyntaxException, IOException {
        ClassLoader classLoader = getClass().getClassLoader();

        URL resource = classLoader.getResource(classpathDirectory);

        return Files.walk(Paths.get(resource.toURI()))
                .filter(Files::isRegularFile)
                .sorted()
                .collect(Collectors.toList());
    }

    private void migrateSqlFile(Path sqlFile) throws IOException, SQLException {
        String content = Files.readString(sqlFile, StandardCharsets.UTF_8);
        String currentHash = hash(content);

        Optional<SqlScript> sqlScript = sqlScriptRepository.find(sqlFile.getFileName().toString());
        if (sqlScript.isPresent()) {
            // validate fingerprint
            if (!currentHash.equals(sqlScript.get().getHash())) {
                throw new MigrationException(String.format("Content of %s has changed", sqlFile));
            }

            return;
        }

        String[] statements = content.split(END_DELIMITER);

        try (Connection connection = dataSource.getConnection();
             Transaction transaction = new Transaction(connection)) {

            for (String statement : statements) {
                queryRunner.execute(connection, statement.trim());
            }

            SqlScript toSave = new SqlScript(sqlFile.getFileName().toString(), currentHash, LocalDateTime.now());
            sqlScriptRepository.save(connection, toSave);

            transaction.commit();
        }
    }

    private String hash(String fileContent) {
        byte[] encodedhash = digest.digest(fileContent.getBytes(StandardCharsets.UTF_8));
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

