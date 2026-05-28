package ru.nsu.chebotareva.task3;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private HikariDataSource dataSource;

    public DatabaseManager(String jdbcUrl, String username, String password, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize + 2);
        this.dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public void createRawSchema() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS raw_relationship CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS raw_person CASCADE;");
            
            stmt.execute("CREATE TABLE raw_person (" +
                         "id VARCHAR(50), " +
                         "first_name VARCHAR(250), " +
                         "last_name VARCHAR(250), " +
                         "gender VARCHAR(50)" +
                         ");");
            
            stmt.execute("CREATE TABLE raw_relationship (" +
                         "person_id VARCHAR(50), " +
                         "rel_type VARCHAR(50), " +
                         "rel_target VARCHAR(250)" +
                         ");");
        }
    }
}
