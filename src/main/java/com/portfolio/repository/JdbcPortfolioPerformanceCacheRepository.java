package com.portfolio.repository;

import com.portfolio.model.PortfolioPerformanceCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class JdbcPortfolioPerformanceCacheRepository implements PortfolioPerformanceCacheRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<PortfolioPerformanceCache> rowMapper = (rs, rowNum) ->
            new PortfolioPerformanceCache(
                    rs.getLong("id"),
                    rs.getLong("portfolio_id"),
                    rs.getDate("performance_date").toLocalDate(),
                    rs.getBigDecimal("invested_cost"),
                    rs.getBigDecimal("cumulative_profit"),
                    rs.getBigDecimal("return_rate"),
                    rs.getBigDecimal("total_value"),
                    rs.getTimestamp("refreshed_at").toLocalDateTime()
            );

    public JdbcPortfolioPerformanceCacheRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PortfolioPerformanceCache> findByPortfolioIdAndPerformanceDates(
            Long portfolioId,
            List<java.time.LocalDate> performanceDates
    ) {
        if (performanceDates.isEmpty()) {
            return List.of();
        }

        String placeholders = performanceDates.stream()
                .map(date -> "?")
                .collect(Collectors.joining(", "));
        List<Object> arguments = new ArrayList<>();
        arguments.add(portfolioId);
        performanceDates.forEach(date -> arguments.add(Date.valueOf(date)));

        return jdbc.query(
                "SELECT * FROM portfolio_performance_cache "
                        + "WHERE portfolio_id = ? AND performance_date IN (" + placeholders + ") "
                        + "ORDER BY performance_date ASC",
                rowMapper,
                arguments.toArray()
        );
    }

    @Override
    public void replaceForPortfolio(
            Long portfolioId,
            List<PortfolioPerformanceCache> entries
    ) {
        deleteByPortfolioId(portfolioId);
        for (PortfolioPerformanceCache entry : entries) {
            jdbc.update(
                    "INSERT INTO portfolio_performance_cache "
                            + "(portfolio_id, performance_date, invested_cost, cumulative_profit, "
                            + "return_rate, total_value, refreshed_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    entry.portfolioId(),
                    Date.valueOf(entry.performanceDate()),
                    entry.investedCost(),
                    entry.cumulativeProfit(),
                    entry.returnRate(),
                    entry.totalValue(),
                    Timestamp.valueOf(entry.refreshedAt())
            );
        }
    }

    @Override
    public void deleteByPortfolioId(Long portfolioId) {
        jdbc.update(
                "DELETE FROM portfolio_performance_cache WHERE portfolio_id = ?",
                portfolioId
        );
    }
}
