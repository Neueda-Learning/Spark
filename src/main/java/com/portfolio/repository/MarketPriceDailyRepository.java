package com.portfolio.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MarketPriceDailyRepository {
    List<LocalDate> findLatestTradeDates(int limit);
    Map<Long, BigDecimal> findClosePricesByTradeDate(LocalDate tradeDate, Collection<Long> stockIds);
    LocalDateTime findLatestFetchedAtForTradeDates(List<LocalDate> tradeDates);
}
