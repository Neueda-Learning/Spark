package com.portfolio.config;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;

@Component
public class DatabaseMigrationRunner implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    
    public DatabaseMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    
    @Override
    public void run(String... args) {
        boolean addedDividendYield = addColumnIfNotExists("stock", "dividend_yield", 
            "ALTER TABLE stock ADD COLUMN dividend_yield DECIMAL(6,4) NOT NULL DEFAULT 0.0000");
        boolean addedDividendDate = addColumnIfNotExists("stock", "dividend_date", 
            "ALTER TABLE stock ADD COLUMN dividend_date DATE");
        
        // 如果刚添加了列，或者是旧数据库，执行 UPDATE 填充数据
        if (addedDividendYield || addedDividendDate) {
            populateDividendData();
        }
    }
    
    private boolean addColumnIfNotExists(String table, String column, String sql) {
        try {
            jdbc.queryForObject("SELECT " + column + " FROM " + table + " LIMIT 1", String.class);
            return false; // 列已存在
        } catch (Exception e) {
            try {
                jdbc.execute(sql);
                return true; // 成功添加了列
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private void populateDividendData() {
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0050, dividend_date = '2026-05-10' WHERE symbol = 'AAPL'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0080, dividend_date = '2026-05-15' WHERE symbol = 'MSFT'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0000, dividend_date = NULL WHERE symbol = 'GOOGL'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0000, dividend_date = NULL WHERE symbol = 'AMZN'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0004, dividend_date = '2026-06-12' WHERE symbol = 'NVDA'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0000, dividend_date = NULL WHERE symbol = 'TSLA'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0040, dividend_date = '2026-06-20' WHERE symbol = 'META'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0300, dividend_date = '2026-06-05' WHERE symbol = 'JPM'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0320, dividend_date = '2026-05-25' WHERE symbol = 'JNJ'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0075, dividend_date = '2026-06-01' WHERE symbol = 'V'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0250, dividend_date = '2026-04-20' WHERE symbol = 'PG'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0360, dividend_date = '2026-05-14' WHERE symbol = 'XOM'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0150, dividend_date = '2026-06-10' WHERE symbol = 'UNH'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0060, dividend_date = '2026-05-08' WHERE symbol = 'MA'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0380, dividend_date = '2026-06-03' WHERE symbol = 'AGG'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0360, dividend_date = '2026-06-04' WHERE symbol = 'BND'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0420, dividend_date = '2026-06-02' WHERE symbol = 'TLT'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0400, dividend_date = '2026-06-03' WHERE symbol = 'LQD'");
    jdbc.execute("UPDATE stock SET dividend_yield = 0.0000, dividend_date = NULL WHERE symbol IN ('USD', 'USDMONEY')");
}
}
