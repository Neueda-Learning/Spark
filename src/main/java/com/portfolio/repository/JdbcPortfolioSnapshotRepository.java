package com.portfolio.repository;

import com.portfolio.model.PortfolioSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class JdbcPortfolioSnapshotRepository implements PortfolioSnapshotRepository {

    private final JdbcTemplate jdbc;

    public JdbcPortfolioSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<PortfolioSnapshot> snapshotRowMapper = (rs, rowNum) -> new PortfolioSnapshot(
            rs.getLong("id"),
            rs.getLong("portfolio_id"),
            rs.getDate("snapshot_date").toLocalDate(),
            rs.getBigDecimal("total_value"),
            rs.getBigDecimal("cash_balance"),
            rs.getBigDecimal("invested_cost")
    );

    @Override
    public PortfolioSnapshot save(PortfolioSnapshot snapshot) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO portfolio_snapshot (portfolio_id, snapshot_date, total_value, cash_balance, invested_cost) " +
                    "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, snapshot.portfolioId());
            ps.setDate(2, Date.valueOf(snapshot.snapshotDate()));
            ps.setBigDecimal(3, snapshot.totalValue());
            ps.setBigDecimal(4, snapshot.cashBalance());
            ps.setBigDecimal(5, snapshot.investedCost());
            return ps;
        }, keyHolder);
        Long generatedId = keyHolder.getKey().longValue();
        return new PortfolioSnapshot(generatedId, snapshot.portfolioId(), snapshot.snapshotDate(),
                snapshot.totalValue(), snapshot.cashBalance(), snapshot.investedCost());
    }

    @Override
    public List<PortfolioSnapshot> findByPortfolioIdOrderBySnapshotDateDesc(Long portfolioId, int limit) {
        return jdbc.query(
                "SELECT * FROM portfolio_snapshot WHERE portfolio_id = ? ORDER BY snapshot_date DESC LIMIT ?",
                snapshotRowMapper,
                portfolioId, limit
        );
    }
}
