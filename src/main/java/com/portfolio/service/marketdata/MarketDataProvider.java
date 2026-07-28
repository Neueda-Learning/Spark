package com.portfolio.service.marketdata;

import com.portfolio.model.MarketPriceDaily;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface MarketDataProvider {

    List<MarketPriceDaily> fetchDailyPrices(
            Long stockId,
            String symbol,
            LocalDate startDate,
            LocalDate endDate
    ) throws IOException;
}
