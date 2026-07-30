package com.portfolio.controller;

import com.portfolio.dto.CandleSeriesResponse;
import com.portfolio.dto.StockInfoResponse;
import com.portfolio.model.Stock;
import com.portfolio.service.CandleInterval;
import com.portfolio.service.CandleService;
import com.portfolio.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;
    private final CandleService candleService;

    public StockController(
            StockService stockService,
            CandleService candleService
    ) {
        this.stockService = stockService;
        this.candleService = candleService;
    }

    /**
     * GET /api/stocks - returns all investable instruments with current price and change percent.
     * Used by the frontend trades page: top rows are held positions, lower rows are not held.
     */
    @GetMapping
    public List<StockInfoResponse> getAllStocks() {
        return stockService.getAllStocksWithPrice();
    }

    /**
     * GET /api/stocks/{id} - returns a single instrument detail.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Stock> getStockById(@PathVariable Long id) {
        return stockService.getStockById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/candles")
    public ResponseEntity<CandleSeriesResponse> getCandles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "DAILY") String interval,
            @RequestParam(required = false) Integer limit
    ) {
        CandleInterval candleInterval = CandleInterval.parse(interval);
        int resolvedLimit = candleInterval.resolveLimit(limit);
        if (stockService.getStockById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(candleService.getCandles(id, candleInterval, resolvedLimit));
    }
}
