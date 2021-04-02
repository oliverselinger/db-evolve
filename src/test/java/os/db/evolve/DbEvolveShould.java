package os.db.evolve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
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
    QueryRunner queryRunner;
    SqlScriptRepository sqlScriptRepository;
    Logger logger = new Logger() {};

    @BeforeEach
    void init() {
        dataSource = DB_EXTENSION.dataSource();
        queryRunner = new QueryRunner(dataSource);
        sqlScriptRepository = new SqlScriptRepository(queryRunner, logger);
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
        List<Path> unsortedList = Stream.of("V10__test.sql", "V2__test.sql", "V1__test.sql").map(Path::of).collect(Collectors.toList());

        Collections.sort(unsortedList, DbEvolve.VERSION_COMPARATOR);

        assertLinesMatch(List.of("V1__test.sql", "V2__test.sql", "V10__test.sql"), unsortedList.stream().map(Path::toString).collect(Collectors.toList()));
    }

    @Test
    void throw_an_exception_if_directory_is_not_found_on_classpath() {
        DbEvolve dbEvolve = new DbEvolve(dataSource, "not_existing", null);
        assertThrows(MigrationException.class, dbEvolve::migrate);
    }

    @Test
    void throw_an_exception_if_file_name_does_not_meet_pattern() {
        DbEvolve dbEvolve = new DbEvolve(dataSource, "sql_wrong_file_name_pattern", null);
        assertThrows(MigrationException.class, dbEvolve::migrate);
    }

    @Test
    void create_and_alter_tables() throws SQLException {
        DbEvolve dbEvolve = new DbEvolve(dataSource);
        dbEvolve.migrate();

        List<SqlScript> scripts = sqlScriptRepository.findAll();
        assertEquals(2, scripts.size());

        SqlScript sqlScript1 = scripts.get(0);
        assertEquals("V1__create_tables.sql", sqlScript1.getName());
        assertEquals("2b19e853bf20b0fc16a34c0adbfa9341e39494dee0128a26ce7b640df555fa03", sqlScript1.getHash());
        assertNotNull(sqlScript1.getTimestamp());

        SqlScript sqlScript2 = scripts.get(1);
        assertEquals("V2__alter_tables.sql", sqlScript2.getName());
        assertEquals("a677106b5ac1ba1aa0724147dd8a392cae90500a1f3af032e0fd6268ca9a7b96", sqlScript2.getHash());
        assertNotNull(sqlScript2.getTimestamp());

        assertDoesNotThrow(() -> queryRunner.execute("INSERT INTO TEST1 VALUES (1, 'ABC')"));
        assertDoesNotThrow(() -> queryRunner.execute("INSERT INTO TEST2 VALUES (2, 'CBA')"));
    }

    @Test
    void not_rerun_already_executed_scripts() throws SQLException {
        DbEvolve dbEvolve = new DbEvolve(dataSource);
        dbEvolve.migrate();

        assertDoesNotThrow(dbEvolve::migrate);

        List<SqlScript> scripts = sqlScriptRepository.findAll();
        assertEquals(2, scripts.size());
    }

    @Test
    void throw_an_exception_if_a_migrated_script_has_changed() throws SQLException {
        DbEvolve dbEvolve = new DbEvolve(dataSource);
        dbEvolve.migrate();

        assertThrows(MigrationException.class, () -> new DbEvolve(dataSource, "sql_changed_file_content", null).migrate());

        List<SqlScript> scripts = sqlScriptRepository.findAll();
        assertEquals(2, scripts.size());
    }

    @Test
    void not_start_the_migration_if_db_is_locked() throws SQLException {
        DbEvolve dbEvolve = new DbEvolve(dataSource);

        LockRepository lockRepository = new LockRepository(queryRunner, logger);
        boolean locked = lockRepository.lock();
        assertTrue(locked);

        boolean migrated = dbEvolve.migrate();
        assertFalse(migrated);

        boolean unlocked = lockRepository.unlock();
        assertTrue(unlocked);

        migrated = dbEvolve.migrate();
        assertTrue(migrated);
    }

    @Test
    void throw_an_exception_if_sql_stmt_is_invalid() {
        DbEvolve dbEvolve = new DbEvolve(dataSource, "sql_invalid_stmt", null);
        MigrationException migrationException = assertThrows(MigrationException.class, dbEvolve::migrate);
        assertEquals(migrationException.getMessage(), "V1__create_tables.sql - Invalid sql statement found at line 9");
    }
}
