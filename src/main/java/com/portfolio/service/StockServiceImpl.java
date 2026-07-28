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
                .map(s -> new StockInfoResponse(
                        s.id(),
                        s.symbol(),
                        s.name(),
                        s.assetType(),
                        s.sector(),
                        s.exchange(),
                        priceService.getCurrentPrice(s.symbol()),
                        priceService.getChangePercent(s.symbol()),
                        s.dividendYield()
                ))
                .toList();
    }
}
