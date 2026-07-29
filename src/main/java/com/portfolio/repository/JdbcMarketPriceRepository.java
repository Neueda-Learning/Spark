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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Override
    public List<LocalDate> findLatestTradeDates(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbc.query(
                "SELECT DISTINCT trade_date FROM market_price_daily ORDER BY trade_date DESC LIMIT ?",
                (rs, rowNum) -> rs.getDate("trade_date").toLocalDate(),
                limit
        );
    }

    @Override
    public Map<Long, BigDecimal> findClosePricesByTradeDate(
            LocalDate tradeDate,
            Collection<Long> stockIds
    ) {
        if (stockIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = stockIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));
        List<Object> arguments = new ArrayList<>();
        arguments.add(Date.valueOf(tradeDate));
        arguments.addAll(stockIds);

        return jdbc.query(
                "SELECT stock_id, close_price FROM market_price_daily "
                        + "WHERE trade_date = ? AND stock_id IN (" + placeholders + ")",
                rs -> {
                    Map<Long, BigDecimal> prices = new LinkedHashMap<>();
                    while (rs.next()) {
                        prices.put(rs.getLong("stock_id"), rs.getBigDecimal("close_price"));
                    }
                    return prices;
                },
                arguments.toArray()
        );
    }

    @Override
    public LocalDateTime findLatestFetchedAtForTradeDates(List<LocalDate> tradeDates) {
        if (tradeDates.isEmpty()) {
            return null;
        }

        String placeholders = tradeDates.stream()
                .map(date -> "?")
                .collect(Collectors.joining(", "));
        Object[] arguments = tradeDates.stream()
                .map(Date::valueOf)
                .toArray();
        Timestamp timestamp = jdbc.query(
                "SELECT MAX(fetched_at) AS latest_fetched_at FROM market_price_daily "
                        + "WHERE trade_date IN (" + placeholders + ")",
                rs -> rs.next() ? rs.getTimestamp("latest_fetched_at") : null,
                arguments
        );
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
