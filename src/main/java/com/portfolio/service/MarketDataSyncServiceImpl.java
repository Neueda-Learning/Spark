package com.portfolio.service;

import com.portfolio.model.MarketPriceDaily;
import com.portfolio.model.Stock;
import com.portfolio.repository.MarketPriceRepository;
import com.portfolio.repository.StockRepository;
import com.portfolio.service.marketdata.MarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarketDataSyncServiceImpl implements MarketDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSyncServiceImpl.class);
    private static final int MAX_ATTEMPTS = 3;

    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final MarketDataProvider marketDataProvider;
    private final Clock clock;
    private final ZoneId marketZone;
    private final LocalTime closeCutoff;
    private final int backfillMonths;
    private final int overlapDays;

    public MarketDataSyncServiceImpl(
            StockRepository stockRepository,
            MarketPriceRepository marketPriceRepository,
            MarketDataProvider marketDataProvider,
            Clock clock,
            @Value("${market-data.sync.zone:America/New_York}") String marketZone,
            @Value("${market-data.sync.close-cutoff:18:30}") String closeCutoff,
            @Value("${market-data.backfill-months:18}") int backfillMonths,
            @Value("${market-data.overlap-days:10}") int overlapDays
    ) {
        this.stockRepository = stockRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.marketDataProvider = marketDataProvider;
        this.clock = clock;
        this.marketZone = ZoneId.of(marketZone);
        this.closeCutoff = LocalTime.parse(closeCutoff);
        this.backfillMonths = backfillMonths;
        this.overlapDays = overlapDays;
    }

    @Override
    public SyncSummary syncAll() {
        LocalDate lastAllowedDate = lastAllowedTradeDate();
        int processed = 0;
        int upserted = 0;
        int skipped = 0;
        int failed = 0;

        for (Stock stock : stockRepository.findAll()) {
            if ("CASH".equals(stock.assetType())) {
                continue;
            }
            processed++;
            LocalDate startDate = marketPriceRepository.findLastTradeDate(stock.id())
                    .map(date -> date.minusDays(overlapDays))
                    .orElseGet(() -> lastAllowedDate.minusMonths(backfillMonths));

            try {
                List<MarketPriceDaily> fetched = fetchWithRetry(
                        stock.id(),
                        stock.symbol(),
                        startDate,
                        lastAllowedDate
                );
                List<MarketPriceDaily> valid = new ArrayList<>();
                for (MarketPriceDaily price : fetched) {
                    if (isValid(price, lastAllowedDate)) {
                        valid.add(price);
                    } else {
                        skipped++;
                        log.warn("Skipping invalid market price: symbol={}, date={}",
                                stock.symbol(), price == null ? null : price.tradeDate());
                    }
                }
                marketPriceRepository.upsertAll(valid);
                upserted += valid.size();
                log.info("Market data sync completed: symbol={}, range={}..{}, upserted={}",
                        stock.symbol(), startDate, lastAllowedDate, valid.size());
            } catch (IOException | RuntimeException ex) {
                failed++;
                log.error("Market data sync failed: symbol={}, range={}..{}, error={}: {}",
                        stock.symbol(), startDate, lastAllowedDate,
                        ex.getClass().getSimpleName(), ex.getMessage());
            }
        }

        SyncSummary summary = new SyncSummary(processed, upserted, skipped, failed);
        log.info("Market data sync summary: {}", summary);
        return summary;
    }

    LocalDate lastAllowedTradeDate() {
        ZonedDateTime marketNow = ZonedDateTime.now(clock).withZoneSameInstant(marketZone);
        if (marketNow.toLocalTime().isBefore(closeCutoff)) {
            return marketNow.toLocalDate().minusDays(1);
        }
        return marketNow.toLocalDate();
    }

    boolean isValid(MarketPriceDaily price, LocalDate lastAllowedDate) {
        if (price == null
                || price.stockId() == null
                || price.tradeDate() == null
                || price.tradeDate().isAfter(lastAllowedDate)
                || price.openPrice() == null
                || price.highPrice() == null
                || price.lowPrice() == null
                || price.closePrice() == null
                || price.fetchedAt() == null
                || price.source() == null) {
            return false;
        }

        BigDecimal zero = BigDecimal.ZERO;
        return price.openPrice().compareTo(zero) > 0
                && price.highPrice().compareTo(zero) > 0
                && price.lowPrice().compareTo(zero) > 0
                && price.closePrice().compareTo(zero) > 0
                && price.highPrice().compareTo(price.openPrice().max(price.closePrice())) >= 0
                && price.lowPrice().compareTo(price.openPrice().min(price.closePrice())) <= 0
                && price.volume() >= 0;
    }

    private List<MarketPriceDaily> fetchWithRetry(
            Long stockId,
            String symbol,
            LocalDate startDate,
            LocalDate endDate
    ) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return marketDataProvider.fetchDailyPrices(stockId, symbol, startDate, endDate);
            } catch (IOException ex) {
                lastFailure = ex;
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                try {
                    Thread.sleep(250L * (1L << (attempt - 1)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Market data retry interrupted", interrupted);
                }
            }
        }
        throw lastFailure;
    }
}
