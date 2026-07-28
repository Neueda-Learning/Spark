package com.portfolio.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class JdbcMarketPriceDailyRepository implements MarketPriceDailyRepository {

    private final JdbcTemplate jdbc;

    public JdbcMarketPriceDailyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LocalDate> findLatestTradeDates(int limit) {
        return jdbc.query(
                "SELECT DISTINCT trade_date FROM market_price_daily ORDER BY trade_date DESC LIMIT ?",
                (rs, rowNum) -> rs.getDate("trade_date").toLocalDate(),
                limit
        );
    }

    @Override
    public Map<Long, BigDecimal> findClosePricesByTradeDate(LocalDate tradeDate, Collection<Long> stockIds) {
        if (stockIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = stockIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(tradeDate));
        args.addAll(stockIds);

        return jdbc.query(
                "SELECT stock_id, close_price FROM market_price_daily WHERE trade_date = ? AND stock_id IN (" + placeholders + ")",
                rs -> {
                    Map<Long, BigDecimal> prices = new LinkedHashMap<>();
                    while (rs.next()) {
                        prices.put(rs.getLong("stock_id"), rs.getBigDecimal("close_price"));
                    }
                    return prices;
                },
                args.toArray()
        );
    }

    @Override
    public LocalDateTime findLatestFetchedAtForTradeDates(List<LocalDate> tradeDates) {
        if (tradeDates.isEmpty()) {
            return null;
        }

        String placeholders = tradeDates.stream().map(date -> "?").collect(Collectors.joining(", "));
        List<Object> args = new ArrayList<>();
        tradeDates.forEach(date -> args.add(Date.valueOf(date)));

        Timestamp timestamp = jdbc.query(
                "SELECT MAX(fetched_at) AS latest_fetched_at FROM market_price_daily WHERE trade_date IN (" + placeholders + ")",
                rs -> rs.next() ? rs.getTimestamp("latest_fetched_at") : null,
                args.toArray()
        );

        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
