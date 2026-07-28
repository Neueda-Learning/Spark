package com.portfolio.repository;

import com.portfolio.model.Stock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcStockRepository implements StockRepository {

    private final JdbcTemplate jdbc;

    public JdbcStockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Stock> stockRowMapper = (rs, rowNum) -> new Stock(
            rs.getLong("id"),
            rs.getString("symbol"),
            rs.getString("name"),
            rs.getString("asset_type"),
            rs.getString("sector"),
            rs.getString("exchange"),
            rs.getString("currency"),
            rs.getBigDecimal("dividend_yield"),
            rs.getDate("dividend_date") != null ? rs.getDate("dividend_date").toLocalDate() : null
    );

    @Override
    public List<Stock> findAll() {
        return jdbc.query("SELECT * FROM stock ORDER BY symbol", stockRowMapper);
    }

    @Override
    public Optional<Stock> findById(Long id) {
        var results = jdbc.query("SELECT * FROM stock WHERE id = ?", stockRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Optional<Stock> findBySymbol(String symbol) {
        var results = jdbc.query("SELECT * FROM stock WHERE symbol = ?", stockRowMapper, symbol);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }
}
