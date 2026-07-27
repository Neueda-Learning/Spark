package com.portfolio.repository;

import com.portfolio.model.Portfolio;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public class JdbcPortfolioRepository implements PortfolioRepository {

    private final JdbcTemplate jdbc;

    public JdbcPortfolioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Portfolio> portfolioRowMapper = (rs, rowNum) -> new Portfolio(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getBigDecimal("cash_balance"),
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    @Override
    public Optional<Portfolio> findById(Long id) {
        var results = jdbc.query("SELECT * FROM portfolio WHERE id = ?", portfolioRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public void updateCashBalance(Long id, BigDecimal newBalance) {
        jdbc.update("UPDATE portfolio SET cash_balance = ? WHERE id = ?", newBalance, id);
    }
}
