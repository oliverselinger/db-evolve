package os.db.evolve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QueryRunnerEvolveShould {

    @RegisterExtension
    static final DbExtension DB_EXTENSION = new DbExtension();

    DataSource dataSource;

    @BeforeEach
    void init() {
        dataSource = DB_EXTENSION.dataSource();
    }

    @Test
    void create_and_alter_tables() throws SQLException, NoSuchAlgorithmException, IOException, URISyntaxException {
        DbEvolve dbEvolve = new DbEvolve(dataSource);
        dbEvolve.migrate();

        // assertTableExists
        // assert Columns Are added
        // sql scripts are registered

//        DatabaseMetaData meta = con.getMetaData();
//        ResultSet res = meta.getTables(null, null, "My_Table_Name",
//                new String[] {"TABLE"});
//        while (res.next()) {
//            System.out.println(
//                    "   "+res.getString("TABLE_CAT")
//                            + ", "+res.getString("TABLE_SCHEM")
//                            + ", "+res.getString("TABLE_NAME")
//                            + ", "+res.getString("TABLE_TYPE")
//                            + ", "+res.getString("REMARKS"));
//        }
    }

    @Test
    void not_rerun_already_executed_scripts() throws SQLException, NoSuchAlgorithmException, IOException, URISyntaxException {
        DbEvolve dbEvolve = new DbEvolve(dataSource);
        dbEvolve.migrate();

        assertDoesNotThrow(dbEvolve::migrate);
        // count sql script entires
    }

    @Test
    void throw_an_exception_if_a_migrated_script_has_changed() throws SQLException, NoSuchAlgorithmException, IOException, URISyntaxException {
        DbEvolve dbEvolve = new DbEvolve(dataSource);
        dbEvolve.migrate();

        assertThrows(MigrationException.class, () -> new DbEvolve(dataSource, "sql_changed_file_content").migrate());
    }
}
