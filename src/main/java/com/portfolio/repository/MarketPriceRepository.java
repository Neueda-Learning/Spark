package com.portfolio.repository;

import com.portfolio.model.MarketPriceDaily;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketPriceRepository {

    void upsertAll(List<MarketPriceDaily> prices);

    Optional<LocalDate> findLastTradeDate(Long stockId);

    Optional<MarketPriceDaily> findLatestByStockId(Long stockId);

    List<MarketPriceDaily> findRecentByStockId(Long stockId, int limit);

    List<MarketPriceDaily> findByStockIdAndTradeDateBetween(
            Long stockId,
            LocalDate startDate,
            LocalDate endDate
    );
}
