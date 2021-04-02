package os.db.evolve;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

class SqlScriptRepository {

    private final QueryRunner queryRunner;
    private final Logger logger;

    SqlScriptRepository(QueryRunner queryRunner, Logger logger) {
        this.queryRunner = queryRunner;
        this.logger = logger;
    }

    void createTable() {
        try {
            queryRunner.execute("CREATE TABLE DB_EVOLVE (name VARCHAR(255) NOT NULL, hash VARCHAR(64) NOT NULL, timestamp TIMESTAMP, PRIMARY KEY (name))");
        } catch (SQLException throwables) {
            // ignore => assumption table already exist. If not migration will fail anyway.
            logger.log(Logger.Level.DEBUG, throwables.getMessage());
            return;
        }
    }

    List<SqlScript> findAll() throws SQLException {
        String selectStmt = "SELECT * FROM DB_EVOLVE ORDER BY TIMESTAMP";
        return queryRunner.selectAll(selectStmt, this::mapToSqlScript);
    }

    void save(Connection connection, SqlScript sqlScript) throws SQLException {
        String insertStmt = "" +
                "INSERT INTO DB_EVOLVE" +
                " (NAME, HASH, TIMESTAMP)" +
                " VALUES" +
                " (?, ?, ?)";

        queryRunner.executeUpdate(connection, insertStmt,
                sqlScript.getName(),
                sqlScript.getHash(),
                Timestamp.valueOf(sqlScript.getTimestamp()));
    }

    private SqlScript mapToSqlScript(ResultSet rs) throws SQLException {
        return new SqlScript(
                rs.getString("NAME"),
                rs.getString("HASH"),
                rs.getTimestamp("TIMESTAMP").toLocalDateTime());
    }
}
