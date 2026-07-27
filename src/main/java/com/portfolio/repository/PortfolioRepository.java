package com.portfolio.repository;

import com.portfolio.model.Portfolio;
import java.math.BigDecimal;
import java.util.Optional;

public interface PortfolioRepository {
    Optional<Portfolio> findById(Long id);
    void updateCashBalance(Long id, BigDecimal newBalance);
}
