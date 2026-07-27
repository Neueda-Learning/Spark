package com.portfolio.repository;

import com.portfolio.model.MarketPriceDaily;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMarketPriceRepository implements MarketPriceRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO market_price_daily (
                stock_id, trade_date, open_price, high_price, low_price,
                close_price, adjusted_close, volume, source, fetched_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                open_price = VALUES(open_price),
                high_price = VALUES(high_price),
                low_price = VALUES(low_price),
                close_price = VALUES(close_price),
                adjusted_close = VALUES(adjusted_close),
                volume = VALUES(volume),
                source = VALUES(source),
                fetched_at = VALUES(fetched_at)
            """;

    private final JdbcTemplate jdbc;

    public JdbcMarketPriceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<MarketPriceDaily> priceRowMapper = (rs, rowNum) -> new MarketPriceDaily(
            rs.getLong("id"),
            rs.getLong("stock_id"),
            rs.getDate("trade_date").toLocalDate(),
            rs.getBigDecimal("open_price"),
            rs.getBigDecimal("high_price"),
            rs.getBigDecimal("low_price"),
            rs.getBigDecimal("close_price"),
            rs.getBigDecimal("adjusted_close"),
            rs.getLong("volume"),
            rs.getString("source"),
            rs.getTimestamp("fetched_at").toInstant()
    );

    @Override
    @Transactional
    public void upsertAll(List<MarketPriceDaily> prices) {
        if (prices.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                MarketPriceDaily price = prices.get(index);
                ps.setLong(1, price.stockId());
                ps.setDate(2, Date.valueOf(price.tradeDate()));
                ps.setBigDecimal(3, price.openPrice());
                ps.setBigDecimal(4, price.highPrice());
                ps.setBigDecimal(5, price.lowPrice());
                ps.setBigDecimal(6, price.closePrice());
                ps.setBigDecimal(7, price.adjustedClose());
                ps.setLong(8, price.volume());
                ps.setString(9, price.source());
                ps.setTimestamp(10, Timestamp.from(price.fetchedAt()));
            }

            @Override
            public int getBatchSize() {
                return prices.size();
            }
        });
    }

    @Override
    public Optional<LocalDate> findLastTradeDate(Long stockId) {
        return jdbc.query(
                        "SELECT MAX(trade_date) FROM market_price_daily WHERE stock_id = ?",
                        (rs, rowNum) -> {
                            Date date = rs.getDate(1);
                            return date == null ? null : date.toLocalDate();
                        },
                        stockId
                ).stream()
                .filter(date -> date != null)
                .findFirst();
    }

    @Override
    public Optional<MarketPriceDaily> findLatestByStockId(Long stockId) {
        return findRecentByStockId(stockId, 1).stream().findFirst();
    }

    @Override
    public List<MarketPriceDaily> findRecentByStockId(Long stockId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbc.query(
                "SELECT * FROM market_price_daily WHERE stock_id = ? ORDER BY trade_date DESC LIMIT ?",
                priceRowMapper,
                stockId,
                limit
        );
    }

    @Override
    public List<MarketPriceDaily> findByStockIdAndTradeDateBetween(
            Long stockId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return jdbc.query(
                """
                SELECT * FROM market_price_daily
                WHERE stock_id = ? AND trade_date BETWEEN ? AND ?
                ORDER BY trade_date
                """,
                priceRowMapper,
                stockId,
                Date.valueOf(startDate),
                Date.valueOf(endDate)
        );
    }
}
