package com.portfolio.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcAiAssistantRepository implements AiAssistantRepository {

    private final JdbcTemplate jdbc;

    public JdbcAiAssistantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<HeldStockRow> findHeldStocks(Long portfolioId) {
        try {
            String holdingTable = firstExistingTable("holding", "holdings", "position", "positions", "portfolio_holding");
            String stockTable = firstExistingTable("stock", "stocks", "security", "securities", "instrument", "instruments", "asset", "assets");
            if (holdingTable == null || stockTable == null) {
                return List.of();
            }

            String holdingPortfolioCol = firstExistingColumn(holdingTable, "portfolio_id", "account_id", "portfolioid");
            String holdingStockCol = firstExistingColumn(holdingTable, "stock_id", "security_id", "instrument_id", "asset_id", "symbol_id");
            String quantityCol = firstExistingColumn(holdingTable, "quantity", "qty", "position_qty", "amount");
            String avgCostCol = firstExistingColumn(holdingTable, "average_cost", "avg_cost", "cost_price", "unit_cost", "avg_price");

            String stockIdCol = firstExistingColumn(stockTable, "id", "stock_id", "security_id", "instrument_id", "asset_id");
            String symbolCol = firstExistingColumn(stockTable, "symbol", "ticker", "code");
            String nameCol = firstExistingColumn(stockTable, "name", "display_name", "security_name", "asset_name");
            String assetTypeCol = firstExistingColumn(stockTable, "asset_type", "asset_class", "type", "category");

            if (holdingPortfolioCol == null || holdingStockCol == null || quantityCol == null
                    || avgCostCol == null || stockIdCol == null || symbolCol == null) {
                return List.of();
            }

            String sql = "SELECT "
                    + "h." + holdingStockCol + " AS stock_id, "
                    + "s." + symbolCol + " AS symbol, "
                    + columnOrDefault("s", nameCol, "symbol") + " AS name, "
                    + columnOrDefault("s", assetTypeCol, "'UNKNOWN'") + " AS asset_type, "
                    + "h." + quantityCol + " AS quantity, "
                    + "h." + avgCostCol + " AS average_cost "
                    + "FROM " + holdingTable + " h "
                    + "JOIN " + stockTable + " s ON s." + stockIdCol + " = h." + holdingStockCol + " "
                    + "WHERE h." + holdingPortfolioCol + " = ? "
                    + "ORDER BY s." + symbolCol;

            return jdbc.query(sql, (rs, rowNum) -> new HeldStockRow(
                    rs.getLong("stock_id"),
                    rs.getString("symbol"),
                    rs.getString("name"),
                    rs.getString("asset_type"),
                    rs.getBigDecimal("quantity"),
                    rs.getBigDecimal("average_cost")
            ), portfolioId);
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public List<AvailableStockRow> findAvailableStocks(Long portfolioId) {
        try {
            String stockTable = firstExistingTable("stock", "stocks", "security", "securities", "instrument", "instruments", "asset", "assets");
            if (stockTable == null) {
                return List.of();
            }

            String stockIdCol = firstExistingColumn(stockTable, "id", "stock_id", "security_id", "instrument_id", "asset_id");
            String symbolCol = firstExistingColumn(stockTable, "symbol", "ticker", "code");
            String nameCol = firstExistingColumn(stockTable, "name", "display_name", "security_name", "asset_name");
            String assetTypeCol = firstExistingColumn(stockTable, "asset_type", "asset_class", "type", "category");

            if (stockIdCol == null || symbolCol == null) {
                return List.of();
            }

            String holdingTable = firstExistingTable("holding", "holdings", "position", "positions", "portfolio_holding");
            if (holdingTable == null) {
                String fallbackSql = "SELECT "
                        + "s." + stockIdCol + " AS stock_id, "
                        + "s." + symbolCol + " AS symbol, "
                        + columnOrDefault("s", nameCol, "symbol") + " AS name, "
                        + columnOrDefault("s", assetTypeCol, "'UNKNOWN'") + " AS asset_type "
                        + "FROM " + stockTable + " s "
                        + "ORDER BY s." + symbolCol + " LIMIT 30";
                return jdbc.query(fallbackSql, (rs, rowNum) -> new AvailableStockRow(
                        rs.getLong("stock_id"),
                        rs.getString("symbol"),
                        rs.getString("name"),
                        rs.getString("asset_type")
                ));
            }

            String holdingPortfolioCol = firstExistingColumn(holdingTable, "portfolio_id", "account_id", "portfolioid");
            String holdingStockCol = firstExistingColumn(holdingTable, "stock_id", "security_id", "instrument_id", "asset_id", "symbol_id");
            String holdingIdCol = firstExistingColumn(holdingTable, "id", "holding_id", "position_id");

            if (holdingPortfolioCol == null || holdingStockCol == null || holdingIdCol == null) {
                return List.of();
            }

            String sql = "SELECT "
                    + "s." + stockIdCol + " AS stock_id, "
                    + "s." + symbolCol + " AS symbol, "
                    + columnOrDefault("s", nameCol, "symbol") + " AS name, "
                    + columnOrDefault("s", assetTypeCol, "'UNKNOWN'") + " AS asset_type "
                    + "FROM " + stockTable + " s "
                    + "LEFT JOIN " + holdingTable + " h "
                    + "ON h." + holdingStockCol + " = s." + stockIdCol + " "
                    + "AND h." + holdingPortfolioCol + " = ? "
                    + "WHERE h." + holdingIdCol + " IS NULL "
                    + "ORDER BY s." + symbolCol;

            return jdbc.query(sql, (rs, rowNum) -> new AvailableStockRow(
                    rs.getLong("stock_id"),
                    rs.getString("symbol"),
                    rs.getString("name"),
                    rs.getString("asset_type")
            ), portfolioId);
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Optional<BigDecimal> findPortfolioCashBalance(Long portfolioId) {
        try {
            String portfolioTable = firstExistingTable("portfolio", "portfolios", "account", "accounts");
            if (portfolioTable == null) {
                return Optional.empty();
            }

            String idCol = firstExistingColumn(portfolioTable, "id", "portfolio_id", "account_id");
            String cashCol = firstExistingColumn(portfolioTable, "cash_balance", "cash", "cash_amount", "available_cash", "balance");
            if (idCol == null || cashCol == null) {
                return Optional.empty();
            }

            String sql = "SELECT " + cashCol + " AS cash_balance FROM " + portfolioTable + " WHERE " + idCol + " = ?";
            List<BigDecimal> rows = jdbc.query(sql, (rs, rowNum) -> rs.getBigDecimal("cash_balance"), portfolioId);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(rows.getFirst());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<PerformancePointRow> findPerformanceSeries(Long portfolioId, int limit) {
        try {
            String snapshotTable = firstExistingTable("portfolio_snapshot", "portfolio_snapshots", "snapshot", "snapshots", "portfolio_history");
            if (snapshotTable == null) {
                return List.of();
            }

            String portfolioCol = firstExistingColumn(snapshotTable, "portfolio_id", "account_id", "portfolioid");
            String dateCol = firstExistingColumn(snapshotTable, "snapshot_date", "date", "as_of_date", "created_date");
            String valueCol = firstExistingColumn(snapshotTable, "total_value", "portfolio_value", "value", "nav");
            String cashCol = firstExistingColumn(snapshotTable, "cash_balance", "cash", "cash_amount", "available_cash");

            if (portfolioCol == null || dateCol == null || valueCol == null) {
                return List.of();
            }

            String sql = "SELECT "
                    + dateCol + " AS snapshot_date, "
                    + valueCol + " AS total_value, "
                    + (cashCol == null ? "0" : cashCol) + " AS cash_balance "
                    + "FROM " + snapshotTable + " "
                    + "WHERE " + portfolioCol + " = ? "
                    + "ORDER BY " + dateCol + " DESC LIMIT ?";

            return jdbc.query(sql, (rs, rowNum) -> {
                Date date = rs.getDate("snapshot_date");
                LocalDate localDate = date == null ? LocalDate.now() : date.toLocalDate();
                return new PerformancePointRow(
                        localDate,
                        rs.getBigDecimal("total_value"),
                        rs.getBigDecimal("cash_balance")
                );
            }, portfolioId, limit);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String firstExistingTable(String... candidates) {
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND LOWER(table_name) = LOWER(?) LIMIT 1";
        for (String candidate : candidates) {
            List<String> rows = jdbc.query(sql, (rs, rowNum) -> rs.getString("table_name"), candidate);
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
        }
        return null;
    }

    private String firstExistingColumn(String tableName, String... candidates) {
        if (tableName == null) {
            return null;
        }
        String sql = "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?) LIMIT 1";
        for (String candidate : candidates) {
            List<String> rows = jdbc.query(sql, (rs, rowNum) -> rs.getString("column_name"), tableName, candidate);
            if (!rows.isEmpty()) {
                return rows.getFirst();
            }
        }
        return null;
    }

    private String columnOrDefault(String alias, String columnName, String fallbackExpression) {
        if (columnName == null || columnName.isBlank()) {
            return fallbackExpression;
        }
        return alias + "." + columnName;
    }
}
