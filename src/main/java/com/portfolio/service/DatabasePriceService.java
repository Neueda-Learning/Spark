package com.portfolio.service;

import com.portfolio.model.MarketPriceDaily;
import com.portfolio.model.Stock;
import com.portfolio.repository.MarketPriceRepository;
import com.portfolio.repository.StockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Profile("!demo & !test")
public class DatabasePriceService implements PriceService {

    private static final BigDecimal CASH_PRICE = BigDecimal.ONE.setScale(2);

    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final Clock clock;
    private final int staleDays;

    public DatabasePriceService(
            StockRepository stockRepository,
            MarketPriceRepository marketPriceRepository,
            Clock clock,
            @Value("${market-data.stale-days:7}") int staleDays
    ) {
        this.stockRepository = stockRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.clock = clock;
        this.staleDays = staleDays;
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        Stock stock = findStock(symbol);
        if (isCash(stock)) {
            return CASH_PRICE;
        }
        return latestPrice(stock).closePrice();
    }

    @Override
    public BigDecimal getChangePercent(String symbol) {
        Stock stock = findStock(symbol);
        if (isCash(stock)) {
            return BigDecimal.ZERO.setScale(2);
        }
        List<MarketPriceDaily> recent = marketPriceRepository.findRecentByStockId(stock.id(), 2);
        if (recent.size() < 2 || recent.get(1).closePrice().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return recent.getFirst().closePrice()
                .subtract(recent.get(1).closePrice())
                .multiply(BigDecimal.valueOf(100))
                .divide(recent.get(1).closePrice(), 2, RoundingMode.HALF_UP);
    }

    @Override
    public LocalDate getPriceDate(String symbol) {
        Stock stock = findStock(symbol);
        return isCash(stock) ? LocalDate.now(clock) : latestPrice(stock).tradeDate();
    }

    @Override
    public String getPriceSource(String symbol) {
        Stock stock = findStock(symbol);
        return isCash(stock) ? "FIXED" : latestPrice(stock).source();
    }

    @Override
    public boolean isStale(String symbol) {
        Stock stock = findStock(symbol);
        if (isCash(stock)) {
            return false;
        }
        long ageInDays = ChronoUnit.DAYS.between(latestPrice(stock).tradeDate(), LocalDate.now(clock));
        return ageInDays > staleDays;
    }

    private Stock findStock(String symbol) {
        return stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + symbol));
    }

    private MarketPriceDaily latestPrice(Stock stock) {
        return marketPriceRepository.findLatestByStockId(stock.id())
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "Market price unavailable for stock: " + stock.symbol()
                ));
    }

    private boolean isCash(Stock stock) {
        return "CASH".equals(stock.assetType());
    }
}
