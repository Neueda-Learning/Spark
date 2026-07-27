package com.portfolio.repository;

import com.portfolio.model.Holding;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface HoldingRepository {
    List<Holding> findByPortfolioId(Long portfolioId);
    Optional<Holding> findByPortfolioIdAndStockId(Long portfolioId, Long stockId);
    Holding save(Holding holding);
    void updateQuantityAndAverageCost(Long id, BigDecimal quantity, BigDecimal averageCost);
    void deleteById(Long id);
}
