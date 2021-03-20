package os.db.evolve;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

class SqlScriptRepository {

    private final QueryRunner queryRunner;

    SqlScriptRepository(QueryRunner queryRunner) {
        this.queryRunner = queryRunner;
    }

    Optional<SqlScript> find(String fileName) throws SQLException {
        String selectStmt = "SELECT * FROM DB_EVOLVE WHERE NAME = ?";
        return Optional.ofNullable(queryRunner.selectOne(selectStmt, this::mapToSqlScript, fileName));
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
