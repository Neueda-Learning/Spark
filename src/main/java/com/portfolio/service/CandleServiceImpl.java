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
import java.util.function.Function;

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
    public CandleSeriesResponse getCandles(Long stockId, CandleInterval interval, int limit) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + stockId));
        if ("CASH".equals(stock.assetType())) {
            return new CandleSeriesResponse(
                    stock.id(),
                    stock.symbol(),
                    interval.name(),
                    "FIXED",
                    null,
                    List.of()
            );
        }

        List<MarketPriceDaily> dailyPrices = new ArrayList<>(
                marketPriceRepository.findRecentByStockId(stockId, queryLimit(interval, limit))
        );
        if (dailyPrices.isEmpty()) {
            return new CandleSeriesResponse(
                    stock.id(),
                    stock.symbol(),
                    interval.name(),
                    null,
                    null,
                    List.of()
            );
        }
        Collections.reverse(dailyPrices);

        List<CandleResponse> candles = switch (interval) {
            case DAILY -> dailyPrices.stream()
                    .map(this::toDailyCandle)
                    .toList();
            case WEEKLY -> aggregate(
                    dailyPrices,
                    date -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            );
            case MONTHLY -> aggregate(dailyPrices, date -> date.withDayOfMonth(1));
        };
        candles = takeLast(candles, limit);

        MarketPriceDaily latest = dailyPrices.getLast();
        return new CandleSeriesResponse(
                stock.id(),
                stock.symbol(),
                interval.name(),
                latest.source(),
                latest.tradeDate(),
                candles
        );
    }

    private int queryLimit(CandleInterval interval, int limit) {
        return switch (interval) {
            case DAILY -> limit;
            case WEEKLY -> limit * 7 + 7;
            case MONTHLY -> limit * 31 + 31;
        };
    }

    private CandleResponse toDailyCandle(MarketPriceDaily price) {
        return new CandleResponse(
                price.tradeDate(),
                price.openPrice(),
                price.highPrice(),
                price.lowPrice(),
                price.closePrice(),
                price.adjustedClose(),
                price.volume()
        );
    }

    private List<CandleResponse> aggregate(
            List<MarketPriceDaily> dailyPrices,
            Function<LocalDate, LocalDate> periodStart
    ) {
        Map<LocalDate, CandleAccumulator> periods = new LinkedHashMap<>();
        for (MarketPriceDaily price : dailyPrices) {
            LocalDate key = periodStart.apply(price.tradeDate());
            periods.computeIfAbsent(key, ignored -> new CandleAccumulator()).add(price);
        }
        return periods.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .toList();
    }

    private List<CandleResponse> takeLast(List<CandleResponse> candles, int limit) {
        if (candles.size() <= limit) {
            return candles;
        }
        return new ArrayList<>(candles.subList(candles.size() - limit, candles.size()));
    }

    private static final class CandleAccumulator {
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
