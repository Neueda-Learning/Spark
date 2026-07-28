package com.portfolio.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Database migration runner.
 * Handles schema changes and data population for existing databases.
 * 
 * Changes in this version:
 * 1. Removes dividend_yield and dividend_date columns from stock table (if exist)
 * 2. Creates dividend_history table
@Component
public class DatabaseMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private final JdbcTemplate jdbc;

    public DatabaseMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        log.info("Running database migration...");

        // // Step 1: Remove old dividend columns from stock table (if they exist)
        removeColumnIfExists("stock", "dividend_yield");
        removeColumnIfExists("stock", "dividend_date");

        // // Step 2: Create dividend_history table
        createDividendHistoryTable();

        log.info("Database migration completed.");
    }

    private void removeColumnIfExists(String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                table,
                column
        );

        if (count == null || count == 0) {
            log.debug("Column {}.{} does not exist, skipping removal", table, column);
            return;
        }

        try {
            jdbc.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
            log.info("Removed column {} from table {}", column, table);
        } catch (Exception e) {
            log.warn("Could not drop column {}.{}: {}", table, column, e.getMessage());
        }
    }

    private void createDividendHistoryTable() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS dividend_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    symbol VARCHAR(20) NOT NULL,
                    ex_date DATE NOT NULL,
                    pay_date DATE NOT NULL,
                    dividend_per_share DECIMAL(10,4) NOT NULL,
                    frequency VARCHAR(20) DEFAULT 'quarterly',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_symbol_ex_date (symbol, ex_date)
                )
            """);
            log.info("dividend_history table ready");
        } catch (Exception e) {
            log.error("Failed to create dividend_history table: {}", e.getMessage());
        }
    }

}
