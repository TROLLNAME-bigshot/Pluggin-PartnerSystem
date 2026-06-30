package grinwin.promodawn.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseManager {

    private final HikariDataSource dataSource;

    public DatabaseManager(ConfigurationSection mysqlSection) {
        HikariConfig config = new HikariConfig();
        String host = mysqlSection.getString("host", "localhost");
        int port = mysqlSection.getInt("port", 3306);
        String database = mysqlSection.getString("database", "promodawn");
        String user = mysqlSection.getString("user", "root");
        String password = mysqlSection.getString("password", "");

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&characterEncoding=UTF-8&serverTimezone=UTC";
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);

        ConfigurationSection pool = mysqlSection.getConfigurationSection("pool");
        if (pool != null) {
            config.setMaximumPoolSize(pool.getInt("maximumPoolSize", 10));
            config.setMinimumIdle(pool.getInt("minimumIdle", 2));
            config.setConnectionTimeout(pool.getLong("connectionTimeoutMs", 30000));
        }

        this.dataSource = new HikariDataSource(config);
    }

    public void initSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            // promo_codes: code (PK), owner, reward_key, created_at
            try (PreparedStatement st = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS promo_codes (" +
                            "code VARCHAR(64) PRIMARY KEY," +
                            "owner VARCHAR(36) NOT NULL," +
                            "reward_key VARCHAR(64) NOT NULL," +
                            "created_at BIGINT NOT NULL," +
                            "partner_percent DOUBLE NOT NULL DEFAULT 0," +
                            "max_uses INT DEFAULT -1," +
                            "custom_success_msg TEXT," +
                            "custom_failure_msg TEXT," +
                            "ignore_global_limit BOOLEAN DEFAULT FALSE" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;")) {
                st.executeUpdate();
            }

            // Migration for existing tables
            try (java.sql.Statement st = connection.createStatement()) {
                // Try to add columns if they don't exist.
                // Using separate try-catch blocks or ignore errors if columns exist is a simple
                // way for migration here without complex checks.
                try {
                    st.executeUpdate("ALTER TABLE promo_codes ADD COLUMN partner_percent DOUBLE NOT NULL DEFAULT 0");
                } catch (SQLException ignored) {
                }
                try {
                    st.executeUpdate("ALTER TABLE promo_codes ADD COLUMN max_uses INT DEFAULT -1");
                } catch (SQLException ignored) {
                }
                try {
                    st.executeUpdate("ALTER TABLE promo_codes ADD COLUMN custom_success_msg TEXT");
                } catch (SQLException ignored) {
                }
                try {
                    st.executeUpdate("ALTER TABLE promo_codes ADD COLUMN custom_failure_msg TEXT");
                } catch (SQLException ignored) {
                }
                try {
                    st.executeUpdate("ALTER TABLE promo_codes ADD COLUMN ignore_global_limit BOOLEAN DEFAULT FALSE");
                } catch (SQLException ignored) {
                }
            }

            // promo_usages: id, player_uuid, player_name, code, used_at, first_join_at
            try (PreparedStatement st = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS promo_usages (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                            "player_uuid CHAR(36) NOT NULL," +
                            "player_name VARCHAR(16) NOT NULL," +
                            "code VARCHAR(64) NOT NULL," +
                            "used_at BIGINT NOT NULL," +
                            "first_join_at BIGINT NOT NULL," +
                            "KEY idx_player_uuid (player_uuid)," +
                            "KEY idx_code (code)," +
                            "CONSTRAINT fk_code FOREIGN KEY (code) REFERENCES promo_codes(code) ON DELETE CASCADE" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;")) {
                st.executeUpdate();
            }

            try (PreparedStatement st = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS partner_transactions (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                            "partner_name VARCHAR(36) NOT NULL," +
                            "player_uuid CHAR(36) NOT NULL," +
                            "player_name VARCHAR(16) NOT NULL," +
                            "promo_code VARCHAR(64) NOT NULL," +
                            "amount DOUBLE NOT NULL," +
                            "partner_percent DOUBLE NOT NULL," +
                            "partner_earned DOUBLE NOT NULL," +
                            "created_at BIGINT NOT NULL," +
                            "KEY idx_partner_name (partner_name)," +
                            "KEY idx_player_name (player_name)," +
                            "KEY idx_created_at (created_at)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;")) {
                st.executeUpdate();
            }

            try (PreparedStatement st = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS partner_payouts (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                            "partner_name VARCHAR(36) NOT NULL," +
                            "amount DOUBLE NOT NULL," +
                            "created_at BIGINT NOT NULL," +
                            "created_by VARCHAR(64) NOT NULL," +
                            "comment VARCHAR(255) NULL," +
                            "KEY idx_partner_payout_name (partner_name)," +
                            "KEY idx_partner_payout_created_at (created_at)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;")) {
                st.executeUpdate();
            }

            try (PreparedStatement st = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS partner_paid_totals (" +
                            "partner_name VARCHAR(36) PRIMARY KEY," +
                            "total_paid DOUBLE NOT NULL," +
                            "updated_at BIGINT NOT NULL," +
                            "updated_by VARCHAR(64) NOT NULL" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;")) {
                st.executeUpdate();
            }

            try (PreparedStatement st = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS partner_logs (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                            "log_type VARCHAR(32) NOT NULL," +
                            "partner_name VARCHAR(36) NULL," +
                            "actor_name VARCHAR(64) NULL," +
                            "details TEXT NOT NULL," +
                            "created_at BIGINT NOT NULL," +
                            "KEY idx_partner_logs_partner (partner_name)," +
                            "KEY idx_partner_logs_created_at (created_at)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;")) {
                st.executeUpdate();
            }

            // Migration: Remove UNIQUE constraint on player_uuid if exists
            try (java.sql.Statement st = connection.createStatement()) {
                // MySQL specific: DROP INDEX uniq_player_uuid ON promo_usages
                try {
                    st.executeUpdate("ALTER TABLE promo_usages DROP INDEX uniq_player_uuid");
                } catch (SQLException ignored) {
                    // Index might not exist or already dropped
                }
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
