package com.portfolio.repository;

import com.portfolio.model.MarketPriceDaily;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MarketPriceRepository {

    void upsertAll(List<MarketPriceDaily> prices);

    Optional<LocalDate> findFirstTradeDate(Long stockId);

    Optional<LocalDate> findLastTradeDate(Long stockId);

    Optional<MarketPriceDaily> findLatestByStockId(Long stockId);

    List<MarketPriceDaily> findRecentByStockId(Long stockId, int limit);

    List<MarketPriceDaily> findByStockIdAndTradeDateBetween(
            Long stockId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<LocalDate> findLatestTradeDates(int limit);

    Map<Long, BigDecimal> findClosePricesByTradeDate(
            LocalDate tradeDate,
            Collection<Long> stockIds
    );

    LocalDateTime findLatestFetchedAtForTradeDates(List<LocalDate> tradeDates);
}
