package os.db.evolve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DbEvolveShould {

    @RegisterExtension
    static final DbExtension DB_EXTENSION = new DbExtension();

    DataSource dataSource;

    @BeforeEach
    void init() {
        dataSource = DB_EXTENSION.dataSource();
    }

    @Test
    void parse_integer_from_file_name_with_proper_pattern() {
        Matcher matcher = DbEvolve.FILE_VERSION_PATTERN.matcher("V123__desc.sql");
        assertTrue(matcher.matches());
        assertEquals(123, Integer.parseInt(matcher.group(1)));
    }

    @Test
    void not_match_file_name_with_wrong_pattern() {
        assertFalse(DbEvolve.FILE_VERSION_PATTERN.matcher("VR__desc.sql").matches());
        assertFalse(DbEvolve.FILE_VERSION_PATTERN.matcher("VRdesc.sql").matches());
        assertFalse(DbEvolve.FILE_VERSION_PATTERN.matcher("123__desc.sql").matches());
        assertFalse(DbEvolve.FILE_VERSION_PATTERN.matcher("123.sql").matches());
    }

    @Test
    void sort_migration_scripts_by_numeric_version() {
        List<String> unsortedList = Stream.of("V10__test.sql", "V2__test.sql", "V1__test.sql").collect(Collectors.toList());

        unsortedList.sort(DbEvolve.VERSION_COMPARATOR);

        assertLinesMatch(List.of("V1__test.sql", "V2__test.sql", "V10__test.sql"), new ArrayList<>(unsortedList));
    }

    @Test
    void throw_an_exception_if_directory_is_not_found_on_classpath() {
        DbEvolve dbEvolve = new DbEvolve(dataSource, "not_existing", null);
        assertThrows(DbEvolve.MigrationException.class, dbEvolve::migrate);
    }

    @Test
    void throw_an_exception_if_file_name_does_not_meet_pattern() {
        DbEvolve dbEvolve = new DbEvolve(dataSource, "sql_wrong_file_name_pattern", null);
        assertThrows(DbEvolve.MigrationException.class, dbEvolve::migrate);
    }

    @Test
    void create_and_alter_tables() throws Exception {
        DbEvolve dbEvolve = new DbEvolve(dataSource);
        dbEvolve.migrate();

        List<SqlScript> scripts = selectAll();
        assertEquals(2, scripts.size());

        SqlScript sqlScript1 = scripts.get(0);
        assertEquals("V1__create_tables.sql", sqlScript1.name);
        assertEquals("2b19e853bf20b0fc16a34c0adbfa9341e39494dee0128a26ce7b640df555fa03", sqlScript1.hash);
        assertNotNull(sqlScript1.timestamp);

        SqlScript sqlScript2 = scripts.get(1);
        assertEquals("V2__alter_tables.sql", sqlScript2.name);
        assertEquals("a677106b5ac1ba1aa0724147dd8a392cae90500a1f3af032e0fd6268ca9a7b96", sqlScript2.hash);
        assertNotNull(sqlScript2.timestamp);

        assertDoesNotThrow(() -> execute("INSERT INTO TEST1 VALUES (1, 'ABC')"));
        assertDoesNotThrow(() -> execute("INSERT INTO TEST2 VALUES (2, 'CBA')"));
    }

    @Test
    void not_rerun_already_executed_scripts() throws Exception {
        DbEvolve dbEvolve = new DbEvolve(dataSource);
        dbEvolve.migrate();

        assertDoesNotThrow(dbEvolve::migrate);

        List<SqlScript> scripts = selectAll();
        assertEquals(2, scripts.size());
    }

    @Test
    void throw_an_exception_if_a_migrated_script_has_changed() throws Exception {
        DbEvolve dbEvolve = new DbEvolve(dataSource);
        dbEvolve.migrate();

        DbEvolve.MigrationException exception = assertThrows(DbEvolve.MigrationException.class, () -> new DbEvolve(dataSource, "sql_changed_file_content", null).migrate());
        assertEquals("Content of V2__alter_tables.sql has changed. Expected hash a677106b5ac1ba1aa0724147dd8a392cae90500a1f3af032e0fd6268ca9a7b96 but was f41274c6b68fa4bd38b1dca865a0cc0da59c277f1b104a8f32ff2cf374516f6d", exception.getMessage());

        List<SqlScript> scripts = selectAll();
        assertEquals(2, scripts.size());
    }

    @Test
    void not_start_the_migration_if_db_is_locked() throws Exception {
        DbEvolve dbEvolve = new DbEvolve(dataSource);

        try (Connection connection = dataSource.getConnection()) {
            boolean locked = dbEvolve.lock(connection);
            assertTrue(locked);
        }

        boolean migrated = dbEvolve.migrate();
        assertFalse(migrated);

        try (Connection connection = dataSource.getConnection()) {
            boolean unlocked = dbEvolve.unlock(connection);
            assertTrue(unlocked);
        }

        migrated = dbEvolve.migrate();
        assertTrue(migrated);
    }

    @Test
    void throw_an_exception_if_sql_stmt_is_invalid() {
        DbEvolve dbEvolve = new DbEvolve(dataSource, "sql_invalid_stmt", null);
        DbEvolve.MigrationException migrationException = assertThrows(DbEvolve.MigrationException.class, dbEvolve::migrate);
        assertEquals(migrationException.getMessage(), "V1__create_tables.sql - Invalid sql statement found at line 9");
    }

    @Test
    void parse_statement_with_custom_delimiter() throws IOException {
        DbEvolve dbEvolve = new DbEvolve(dataSource);

        String postgresFunction = "" +
                "CREATE FUNCTION public.message_content_vector_update() RETURNS trigger\n" +
                "    LANGUAGE plpgsql\n" +
                "    AS $$\n" +
                "BEGIN\n" +
                "    NEW.content_vector = to_tsvector('pg_catalog.english', COALESCE(NEW.content, '') || ' ' || COALESCE(NEW.subject, '') || ' ' || COALESCE(NEW.sender_id, '') || ' ' ||\n" +
                "                                                           COALESCE(NEW.recipients_semicolon_separated, ''));\n" +
                "    RETURN NEW;\n" +
                "END\n" +
                "$$;\n" +
                "##";

        String functionWithCustomDelimiter = "DELIMITER ##\n" + postgresFunction + "##\n";

        dbEvolve.parseAndExecuteStatements("file.sql", new BufferedReader(new StringReader(functionWithCustomDelimiter)), stmt -> assertEquals(postgresFunction, stmt));
    }

    @Test
    void create_hash_for_file() throws IOException {
        String hash = DbEvolve.hash(Path.of("src/test/resources/sql/V1__create_tables.sql"));
        assertEquals("2b19e853bf20b0fc16a34c0adbfa9341e39494dee0128a26ce7b640df555fa03", hash);
    }

    private int execute(String sqlStatement) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sqlStatement);
        }
    }

    private List<SqlScript> selectAll() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement ps = connection.createStatement()) {
            ResultSet rs = ps.executeQuery("SELECT * FROM DB_EVOLVE ORDER BY TIMESTAMP");

            List<SqlScript> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new SqlScript(rs.getString("NAME"), rs.getString("HASH"), rs.getTimestamp("TIMESTAMP").toLocalDateTime()));
            }
            return result;
        }
    }

    private static class SqlScript {
        private final String name;
        private final String hash;
        private final LocalDateTime timestamp;

        public SqlScript(String name, String hash, LocalDateTime timestamp) {
            this.name = name;
            this.hash = hash;
            this.timestamp = timestamp;
        }
    }
}
