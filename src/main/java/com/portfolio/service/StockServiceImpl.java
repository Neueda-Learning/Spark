package com.portfolio.service;

import com.portfolio.dto.StockInfoResponse;
import com.portfolio.model.Stock;
import com.portfolio.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StockServiceImpl implements StockService {

    private final StockRepository stockRepository;
    private final PriceService priceService;

    public StockServiceImpl(StockRepository stockRepository, PriceService priceService) {
        this.stockRepository = stockRepository;
        this.priceService = priceService;
    }

    @Override
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    @Override
    public Optional<Stock> getStockById(Long id) {
        return stockRepository.findById(id);
    }

    @Override
    public List<StockInfoResponse> getAllStocksWithPrice() {
        return stockRepository.findAll().stream()
                .map(this::toStockInfoResponse)
                .toList();
    }

    private StockInfoResponse toStockInfoResponse(Stock stock) {
        try {
            return new StockInfoResponse(
                    stock.id(),
                    stock.symbol(),
                    stock.name(),
                    stock.assetType(),
                    stock.sector(),
                    stock.exchange(),
                    priceService.getCurrentPrice(stock.symbol()),
                    priceService.getChangePercent(stock.symbol()),
                    priceService.getPriceDate(stock.symbol()),
                    priceService.getPriceSource(stock.symbol()),
                    priceService.isStale(stock.symbol())
            );
        } catch (MarketDataUnavailableException ex) {
            return new StockInfoResponse(
                    stock.id(),
                    stock.symbol(),
                    stock.name(),
                    stock.assetType(),
                    stock.sector(),
                    stock.exchange(),
                    null,
                    null,
                    null,
                    null,
                    true
            );
        }
    }
}
