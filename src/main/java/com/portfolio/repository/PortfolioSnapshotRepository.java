package com.portfolio.repository;

import com.portfolio.model.PortfolioSnapshot;
import java.util.List;

public interface PortfolioSnapshotRepository {
    PortfolioSnapshot save(PortfolioSnapshot snapshot);
    List<PortfolioSnapshot> findByPortfolioIdOrderBySnapshotDateDesc(Long portfolioId, int limit);
}
