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
 * 3. Creates user_dividend table
 * 4. Populates dividend_history with quarterly dividend data for all dividend-paying stocks
 */
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
        // createDividendHistoryTable();

        // // Step 3: Create user_dividend table
        // createUserDividendTable();

        // // Step 4: Populate dividend_history data (only if table is empty)
        // populateDividendHistoryIfEmpty();

        log.info("Database migration completed.");
    }

    private void removeColumnIfExists(String table, String column) {
        try {
            jdbc.queryForObject("SELECT " + column + " FROM " + table + " LIMIT 1", String.class);
            // Column exists, try to drop it
            try {
                jdbc.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
                log.info("Removed column {} from table {}", column, table);
            } catch (Exception e) {
                log.warn("Could not drop column {}.{}: {}", table, column, e.getMessage());
            }
        } catch (Exception e) {
            // Column doesn't exist, nothing to do
            log.debug("Column {}.{} does not exist, skipping removal", table, column);
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

    private void createUserDividendTable() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS user_dividend (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    portfolio_id BIGINT NOT NULL,
                    symbol VARCHAR(20) NOT NULL,
                    ex_date DATE NOT NULL,
                    pay_date DATE NOT NULL,
                    shares_held INT NOT NULL,
                    dividend_per_share DECIMAL(10,4) NOT NULL,
                    gross_amount DECIMAL(12,2) NOT NULL,
                    tax_rate DECIMAL(6,4) NOT NULL DEFAULT 0.0000,
                    net_amount DECIMAL(12,2) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'pending',
                    paid_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_portfolio_symbol_ex (portfolio_id, symbol, ex_date)
                )
            """);
            log.info("user_dividend table ready");
        } catch (Exception e) {
            log.error("Failed to create user_dividend table: {}", e.getMessage());
        }
    }

    private void populateDividendHistoryIfEmpty() {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dividend_history", Integer.class);
            if (count != null && count > 0) {
                log.info("dividend_history already has {} records, skipping population", count);
                return;
            }

            log.info("Populating dividend_history data...");

            // AAPL: $0.25/share quarterly
            insertDividend("AAPL", "2025-08-08", "2025-08-14", 0.2500);
            insertDividend("AAPL", "2025-11-07", "2025-11-13", 0.2500);
            insertDividend("AAPL", "2026-02-06", "2026-02-12", 0.2500);
            insertDividend("AAPL", "2026-05-08", "2026-05-14", 0.2500);

            // MSFT: $0.75/share quarterly
            insertDividend("MSFT", "2025-08-14", "2025-09-04", 0.7500);
            insertDividend("MSFT", "2025-11-20", "2025-12-11", 0.7500);
            insertDividend("MSFT", "2026-02-19", "2026-03-12", 0.7500);
            insertDividend("MSFT", "2026-05-14", "2026-06-11", 0.7500);

            // NVDA: $0.01/share quarterly
            insertDividend("NVDA", "2025-09-05", "2025-09-12", 0.0100);
            insertDividend("NVDA", "2025-12-05", "2025-12-12", 0.0100);
            insertDividend("NVDA", "2026-03-06", "2026-03-13", 0.0100);
            insertDividend("NVDA", "2026-06-12", "2026-06-19", 0.0100);

            // META: $0.50/share quarterly
            insertDividend("META", "2025-09-15", "2025-09-25", 0.5000);
            insertDividend("META", "2025-12-15", "2026-01-09", 0.5000);
            insertDividend("META", "2026-03-15", "2026-03-26", 0.5000);
            insertDividend("META", "2026-06-15", "2026-06-26", 0.5000);

            // JPM: $1.05/share quarterly
            insertDividend("JPM", "2025-09-04", "2025-10-01", 1.0500);
            insertDividend("JPM", "2025-12-04", "2026-01-02", 1.0500);
            insertDividend("JPM", "2026-03-05", "2026-04-01", 1.0500);
            insertDividend("JPM", "2026-06-05", "2026-07-01", 1.0500);

            // JNJ: $1.24/share quarterly
            insertDividend("JNJ", "2025-08-25", "2025-09-09", 1.2400);
            insertDividend("JNJ", "2025-11-24", "2025-12-09", 1.2400);
            insertDividend("JNJ", "2026-02-23", "2026-03-10", 1.2400);
            insertDividend("JNJ", "2026-05-25", "2026-06-09", 1.2400);

            // V: $0.56/share quarterly
            insertDividend("V", "2025-09-04", "2025-10-01", 0.5600);
            insertDividend("V", "2025-12-04", "2026-01-02", 0.5600);
            insertDividend("V", "2026-03-05", "2026-04-01", 0.5600);
            insertDividend("V", "2026-06-04", "2026-07-01", 0.5600);

            // PG: $1.0175/share quarterly
            insertDividend("PG", "2025-10-23", "2025-11-17", 1.0175);
            insertDividend("PG", "2026-01-22", "2026-02-15", 1.0175);
            insertDividend("PG", "2026-04-20", "2026-05-15", 1.0175);
            insertDividend("PG", "2026-07-23", "2026-08-15", 1.0175);

            // XOM: $0.95/share quarterly
            insertDividend("XOM", "2025-08-14", "2025-09-10", 0.9500);
            insertDividend("XOM", "2025-11-13", "2025-12-10", 0.9500);
            insertDividend("XOM", "2026-02-12", "2026-03-10", 0.9500);
            insertDividend("XOM", "2026-05-14", "2026-06-10", 0.9500);

            // UNH: $2.00/share quarterly
            insertDividend("UNH", "2025-09-05", "2025-09-30", 2.0000);
            insertDividend("UNH", "2025-12-05", "2025-12-30", 2.0000);
            insertDividend("UNH", "2026-03-06", "2026-03-30", 2.0000);
            insertDividend("UNH", "2026-06-05", "2026-06-30", 2.0000);

            // MA: $0.77/share quarterly
            insertDividend("MA", "2025-08-07", "2025-08-20", 0.7700);
            insertDividend("MA", "2025-11-06", "2025-11-20", 0.7700);
            insertDividend("MA", "2026-02-05", "2026-02-20", 0.7700);
            insertDividend("MA", "2026-05-07", "2026-05-20", 0.7700);

            // AGG: $0.93/share quarterly (bond ETF)
            insertDividend("AGG", "2025-09-03", "2025-09-10", 0.9300);
            insertDividend("AGG", "2025-12-03", "2025-12-10", 0.9300);
            insertDividend("AGG", "2026-03-04", "2026-03-11", 0.9300);
            insertDividend("AGG", "2026-06-03", "2026-06-10", 0.9300);

            // BND: $0.44/share quarterly (bond ETF)
            insertDividend("BND", "2025-09-04", "2025-09-10", 0.4400);
            insertDividend("BND", "2025-12-04", "2025-12-10", 0.4400);
            insertDividend("BND", "2026-03-05", "2026-03-11", 0.4400);
            insertDividend("BND", "2026-06-04", "2026-06-10", 0.4400);

            // TLT: $0.66/share quarterly (bond ETF)
            insertDividend("TLT", "2025-09-02", "2025-09-08", 0.6600);
            insertDividend("TLT", "2025-12-02", "2025-12-08", 0.6600);
            insertDividend("TLT", "2026-03-03", "2026-03-09", 0.6600);
            insertDividend("TLT", "2026-06-02", "2026-06-08", 0.6600);

            // LQD: $0.65/share quarterly (bond ETF)
            insertDividend("LQD", "2025-09-03", "2025-09-10", 0.6500);
            insertDividend("LQD", "2025-12-03", "2025-12-10", 0.6500);
            insertDividend("LQD", "2026-03-04", "2026-03-11", 0.6500);
            insertDividend("LQD", "2026-06-03", "2026-06-10", 0.6500);

            log.info("dividend_history data populated (64 records)");
        } catch (Exception e) {
            log.error("Failed to populate dividend_history: {}", e.getMessage());
        }
    }

    private void insertDividend(String symbol, String exDate, String payDate, double dividendPerShare) {
        jdbc.update(
            "INSERT INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES (?, ?, ?, ?, 'quarterly')",
            symbol, exDate, payDate, dividendPerShare
        );
    }
}
