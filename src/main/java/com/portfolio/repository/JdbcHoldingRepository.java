package com.portfolio.repository;

import com.portfolio.model.Holding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcHoldingRepository implements HoldingRepository {

    private final JdbcTemplate jdbc;

    public JdbcHoldingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Holding> holdingRowMapper = (rs, rowNum) -> new Holding(
            rs.getLong("id"),
            rs.getLong("portfolio_id"),
            rs.getLong("stock_id"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("average_cost")
    );

    @Override
    public List<Holding> findByPortfolioId(Long portfolioId) {
        return jdbc.query(
                "SELECT * FROM holding WHERE portfolio_id = ?",
                holdingRowMapper,
                portfolioId
        );
    }

    @Override
    public Optional<Holding> findByPortfolioIdAndStockId(Long portfolioId, Long stockId) {
        var results = jdbc.query(
                "SELECT * FROM holding WHERE portfolio_id = ? AND stock_id = ?",
                holdingRowMapper,
                portfolioId, stockId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Holding save(Holding holding) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO holding (portfolio_id, stock_id, quantity, average_cost) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, holding.portfolioId());
            ps.setLong(2, holding.stockId());
            ps.setBigDecimal(3, holding.quantity());
            ps.setBigDecimal(4, holding.averageCost());
            return ps;
        }, keyHolder);
        Long generatedId = keyHolder.getKey().longValue();
        return new Holding(generatedId, holding.portfolioId(), holding.stockId(),
                holding.quantity(), holding.averageCost());
    }

    @Override
    public void updateQuantityAndAverageCost(Long id, BigDecimal quantity, BigDecimal averageCost) {
        jdbc.update("UPDATE holding SET quantity = ?, average_cost = ? WHERE id = ?",
                quantity, averageCost, id);
    }

    @Override
    public void deleteById(Long id) {
        jdbc.update("DELETE FROM holding WHERE id = ?", id);
    }
}
