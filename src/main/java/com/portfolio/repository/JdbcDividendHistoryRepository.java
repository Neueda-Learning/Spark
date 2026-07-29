package com.portfolio.repository;

import com.portfolio.model.DividendHistory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcDividendHistoryRepository implements DividendHistoryRepository {

    private final JdbcTemplate jdbc;

    public JdbcDividendHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<DividendHistory> rowMapper = (rs, rowNum) -> new DividendHistory(
            rs.getLong("id"),
            rs.getString("symbol"),
            rs.getDate("ex_date").toLocalDate(),
            rs.getDate("pay_date").toLocalDate(),
            rs.getBigDecimal("dividend_per_share"),
            rs.getString("frequency"),
            rs.getDate("created_at").toLocalDate()
    );

    @Override
    public Optional<DividendHistory> findById(Long id) {
        List<DividendHistory> results = jdbc.query(
                "SELECT * FROM dividend_history WHERE id = ?",
                rowMapper, id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<DividendHistory> findBySymbol(String symbol) {
        return jdbc.query(
                "SELECT * FROM dividend_history WHERE symbol = ? ORDER BY ex_date",
                rowMapper, symbol
        );
    }

    @Override
    public List<DividendHistory> findAll() {
        return jdbc.query("SELECT * FROM dividend_history ORDER BY symbol, ex_date", rowMapper);
    }

    @Override
    public List<DividendHistory> findUpToDate(LocalDate date) {
        return jdbc.query(
                "SELECT * FROM dividend_history WHERE ex_date <= ? ORDER BY symbol, ex_date",
                rowMapper, Date.valueOf(date)
        );
    }
}