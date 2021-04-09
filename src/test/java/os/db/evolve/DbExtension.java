package os.db.evolve;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;

public class DbExtension implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {

    private final static Logger log = LoggerFactory.getLogger(DbExtension.class);
    private final static DatabaseTestConfig databaseTestConfig = findDatabaseConfig();

    private HikariDataSource dataSource;

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(databaseTestConfig.jdbcUrl());
        config.setDriverClassName(databaseTestConfig.driver());
        config.setUsername(databaseTestConfig.user());
        config.setPassword(databaseTestConfig.password());
        config.setMaximumPoolSize(databaseTestConfig.maxPoolSize());

        dataSource = new HikariDataSource(config);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws SQLException {
        databaseTestConfig.clearDb(dataSource);
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        dataSource.close();
    }

    private static DatabaseTestConfig findDatabaseConfig() {
        String testDB = System.getenv("TEST_DB");

        if (testDB == null) {
            log.info("Configuring H2 database");
            return new H2DatabaseTestConfig();
        }

        switch (testDB) {
            case "POSTGRES":
                log.info("Configuring Postgres database");
                return new PostgresDatabaseTestConfig();
            case "MYSQL":
                log.info("Configuring MySql database");
                return new MySqlDatabaseTestConfig();
            default:
                throw new IllegalArgumentException("Unknown test database");
        }
    }
}