package com.portfolio.repository;

import com.portfolio.model.Transaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcTransactionRepository implements TransactionRepository {

    private final JdbcTemplate jdbc;

    public JdbcTransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Transaction> transactionRowMapper = (rs, rowNum) -> new Transaction(
            rs.getLong("id"),
            rs.getLong("portfolio_id"),
            rs.getLong("stock_id"),
            rs.getString("type"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("unit_price"),
            rs.getBigDecimal("total_amount"),
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    @Override
    public Transaction save(Transaction transaction) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO transaction (portfolio_id, stock_id, type, quantity, unit_price, total_amount) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, transaction.portfolioId());
            ps.setLong(2, transaction.stockId());
            ps.setString(3, transaction.type());
            ps.setBigDecimal(4, transaction.quantity());
            ps.setBigDecimal(5, transaction.unitPrice());
            ps.setBigDecimal(6, transaction.totalAmount());
            return ps;
        }, keyHolder);
        Long generatedId = keyHolder.getKey().longValue();
        return new Transaction(generatedId, transaction.portfolioId(), transaction.stockId(),
                transaction.type(), transaction.quantity(), transaction.unitPrice(),
                transaction.totalAmount(), transaction.createdAt());
    }

    @Override
    public List<Transaction> findByPortfolioId(Long portfolioId) {
        return jdbc.query(
                "SELECT * FROM transaction WHERE portfolio_id = ? ORDER BY created_at DESC",
                transactionRowMapper,
                portfolioId
        );
    }

    @Override
    public List<Transaction> findByPortfolioIdAndStockIdBeforeDate(Long portfolioId, Long stockId, LocalDate date) {
        return jdbc.query(
                "SELECT * FROM transaction WHERE portfolio_id = ? AND stock_id = ? AND DATE(created_at) <= ? ORDER BY created_at ASC",
                transactionRowMapper,
                portfolioId, stockId, date
        );
    }
}
