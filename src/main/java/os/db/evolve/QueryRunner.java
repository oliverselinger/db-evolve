package os.db.evolve;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

class QueryRunner {

    private final DataSource dataSource;

    QueryRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void execute(String... sqlStatements) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, sqlStatements);
        }
    }

    public void execute(Connection connection, String... sqlStatements) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sqlStatement : sqlStatements) {
                statement.execute(sqlStatement);
            }
        }
    }

    public <T> T selectOne(String sql, RowMapper<T> rowMapper, Object... params) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            List<T> selection = selectAll(connection, sql, rowMapper, params);

            if (selection.isEmpty()) {
                return null;
            }

            if (selection.size() > 1) {
                throw new RuntimeException("Too many results");
            }

            return selection.get(0);
        }
    }

    public <T> List<T> selectAll(String sql, RowMapper<T> rowMapper, Object... params) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return selectAll(connection, sql, rowMapper, params);
        }
    }

    public <T> List<T> selectAll(Connection connection, String sql, RowMapper<T> rowMapper, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int cnt = 0;

            if (params != null) {
                for (Object param : params) {
                    ps.setObject(++cnt, param);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(rowMapper.map(rs));
                }
                return result;
            }
        }
    }

    public int executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return executeUpdate(connection, sql, params);
        }
    }

    public int executeUpdate(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            int cnt = 0;
            for (Object param : params) {
                ps.setObject(++cnt, param);
            }

            return ps.executeUpdate();
        }
    }

    public interface RowMapper<R> {
        R map(ResultSet rs) throws SQLException;
    }

}
