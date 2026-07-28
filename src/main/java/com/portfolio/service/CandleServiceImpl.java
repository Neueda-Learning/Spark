package com.portfolio.service;

import com.portfolio.dto.CandleResponse;
import com.portfolio.dto.CandleSeriesResponse;
import com.portfolio.model.MarketPriceDaily;
import com.portfolio.model.Stock;
import com.portfolio.repository.MarketPriceRepository;
import com.portfolio.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CandleServiceImpl implements CandleService {

    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;

    public CandleServiceImpl(
            StockRepository stockRepository,
            MarketPriceRepository marketPriceRepository
    ) {
        this.stockRepository = stockRepository;
        this.marketPriceRepository = marketPriceRepository;
    }

    @Override
    public CandleSeriesResponse getWeeklyCandles(Long stockId, int weeks) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + stockId));
        if ("CASH".equals(stock.assetType())) {
            return new CandleSeriesResponse(stock.id(), stock.symbol(), "WEEKLY", "FIXED", null, List.of());
        }

        List<MarketPriceDaily> dailyPrices = new ArrayList<>(
                marketPriceRepository.findRecentByStockId(stockId, weeks * 7 + 7)
        );
        if (dailyPrices.isEmpty()) {
            return new CandleSeriesResponse(stock.id(), stock.symbol(), "WEEKLY", null, null, List.of());
        }
        Collections.reverse(dailyPrices);

        Map<LocalDate, WeeklyAccumulator> weekly = new LinkedHashMap<>();
        for (MarketPriceDaily price : dailyPrices) {
            LocalDate weekStart = price.tradeDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weekly.computeIfAbsent(weekStart, ignored -> new WeeklyAccumulator())
                    .add(price);
        }

        List<CandleResponse> candles = weekly.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .toList();
        if (candles.size() > weeks) {
            candles = new ArrayList<>(candles.subList(candles.size() - weeks, candles.size()));
        }

        MarketPriceDaily latest = dailyPrices.getLast();
        return new CandleSeriesResponse(
                stock.id(),
                stock.symbol(),
                "WEEKLY",
                latest.source(),
                latest.tradeDate(),
                candles
        );
    }

    private static final class WeeklyAccumulator {
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal adjustedClose;
        private long volume;

        private void add(MarketPriceDaily price) {
            if (open == null) {
                open = price.openPrice();
                high = price.highPrice();
                low = price.lowPrice();
            } else {
                high = high.max(price.highPrice());
                low = low.min(price.lowPrice());
            }
            close = price.closePrice();
            adjustedClose = price.adjustedClose();
            volume = Math.addExact(volume, price.volume());
        }

        private CandleResponse toResponse(LocalDate weekStart) {
            return new CandleResponse(
                    weekStart,
                    open,
                    high,
                    low,
                    close,
                    adjustedClose,
                    volume
            );
        }
    }
}
