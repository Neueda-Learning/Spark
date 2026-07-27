package com.portfolio.service;

import com.portfolio.dto.StockInfoResponse;
import com.portfolio.model.Stock;
import java.util.List;
import java.util.Optional;

public interface StockService {
    List<Stock> getAllStocks();
    Optional<Stock> getStockById(Long id);
    List<StockInfoResponse> getAllStocksWithPrice();
}
