package com.portfolio.repository;

import com.portfolio.model.UserDividend;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcUserDividendRepository implements UserDividendRepository {

    private final JdbcTemplate jdbc;

    public JdbcUserDividendRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<UserDividend> rowMapper = (rs, rowNum) -> new UserDividend(
            rs.getLong("id"),
            rs.getLong("portfolio_id"),
            rs.getString("symbol"),
            rs.getDate("ex_date").toLocalDate(),
            rs.getDate("pay_date").toLocalDate(),
            rs.getInt("shares_held"),
            rs.getBigDecimal("dividend_per_share"),
            rs.getBigDecimal("gross_amount"),
            rs.getBigDecimal("tax_rate"),
            rs.getBigDecimal("net_amount"),
            rs.getString("status"),
            rs.getTimestamp("paid_at") != null ? rs.getTimestamp("paid_at").toLocalDateTime() : null,
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null
    );

    @Override
    public UserDividend save(UserDividend ud) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO user_dividend (portfolio_id, symbol, ex_date, pay_date, shares_held, " +
                "dividend_per_share, gross_amount, tax_rate, net_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, ud.portfolioId());
            ps.setString(2, ud.symbol());
            ps.setDate(3, Date.valueOf(ud.exDate()));
            ps.setDate(4, Date.valueOf(ud.payDate()));
            ps.setInt(5, ud.sharesHeld());
            ps.setBigDecimal(6, ud.dividendPerShare());
            ps.setBigDecimal(7, ud.grossAmount());
            ps.setBigDecimal(8, ud.taxRate());
            ps.setBigDecimal(9, ud.netAmount());
            ps.setString(10, ud.status());
            return ps;
        }, keyHolder);
        
        Long id = keyHolder.getKey().longValue();
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<UserDividend> findById(Long id) {
        var results = jdbc.query("SELECT * FROM user_dividend WHERE id = ?", rowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Optional<UserDividend> findByPortfolioIdAndSymbolAndExDate(Long portfolioId, String symbol, LocalDate exDate) {
        var results = jdbc.query(
            "SELECT * FROM user_dividend WHERE portfolio_id = ? AND symbol = ? AND ex_date = ?",
            rowMapper, portfolioId, symbol, Date.valueOf(exDate)
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public List<UserDividend> findPendingDividends(Long portfolioId, LocalDate asOfDate) {
        return jdbc.query(
            "SELECT * FROM user_dividend WHERE portfolio_id = ? AND status = 'pending' AND pay_date <= ?",
            rowMapper, portfolioId, Date.valueOf(asOfDate)
        );
    }

    @Override
    public List<UserDividend> findByPortfolioIdAndDateRange(Long portfolioId, LocalDate startDate, LocalDate endDate) {
        return jdbc.query(
            "SELECT * FROM user_dividend WHERE portfolio_id = ? AND ex_date BETWEEN ? AND ? ORDER BY ex_date",
            rowMapper, portfolioId, Date.valueOf(startDate), Date.valueOf(endDate)
        );
    }

    @Override
    public void markAsPaid(Long id) {
        jdbc.update(
            "UPDATE user_dividend SET status = 'paid', paid_at = ? WHERE id = ?",
            Timestamp.valueOf(LocalDateTime.now()), id
        );
    }

    @Override
    public BigDecimal sumPendingByPortfolioId(Long portfolioId) {
        BigDecimal result = jdbc.queryForObject(
            "SELECT COALESCE(SUM(net_amount), 0) FROM user_dividend WHERE portfolio_id = ? AND status = 'pending'",
            BigDecimal.class, portfolioId
        );
        return result != null ? result : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal sumPaidByPortfolioIdAndDateRange(Long portfolioId, LocalDate startDate, LocalDate endDate) {
        BigDecimal result = jdbc.queryForObject(
            "SELECT COALESCE(SUM(net_amount), 0) FROM user_dividend WHERE portfolio_id = ? AND status = 'paid' AND ex_date BETWEEN ? AND ?",
            BigDecimal.class, portfolioId, Date.valueOf(startDate), Date.valueOf(endDate)
        );
        return result != null ? result : BigDecimal.ZERO;
    }
}
