package com.portfolio.controller;

import com.portfolio.dto.CandleSeriesResponse;
import com.portfolio.dto.PriceHistoryResponse;
import com.portfolio.dto.StockInfoResponse;
import com.portfolio.model.Stock;
import com.portfolio.service.CandleService;
import com.portfolio.service.PriceService;
import com.portfolio.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;
    private final PriceService priceService;
    private final CandleService candleService;

    public StockController(
            StockService stockService,
            PriceService priceService,
            CandleService candleService
    ) {
        this.stockService = stockService;
        this.priceService = priceService;
        this.candleService = candleService;
    }

    /**
     * GET /api/stocks — 获取所有可投资标的（含当前价格和涨跌幅）
     * 前端页面2使用：上部分标记已持有，下部分标记未持有
     */
    @GetMapping
    public List<StockInfoResponse> getAllStocks() {
        return stockService.getAllStocksWithPrice();
    }

    /**
     * GET /api/stocks/{id} — 获取单个标的详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Stock> getStockById(@PathVariable Long id) {
        return stockService.getStockById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/stocks/{id}/prices — 获取某标的过去7天价格历史
     * 前端页面2：鼠标悬停弹窗中的价格走势图数据
     */
    @GetMapping("/{id}/prices")
    public ResponseEntity<List<PriceHistoryResponse>> getPriceHistory(@PathVariable Long id) {
        return stockService.getStockById(id)
                .map(stock -> ResponseEntity.ok(priceService.getSevenDayPriceHistory(stock.symbol())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/candles")
    public ResponseEntity<CandleSeriesResponse> getCandles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "WEEKLY") String interval,
            @RequestParam(defaultValue = "52") int weeks
    ) {
        if (!"WEEKLY".equalsIgnoreCase(interval)) {
            throw new IllegalArgumentException("Unsupported candle interval: " + interval);
        }
        if (weeks < 1 || weeks > 104) {
            throw new IllegalArgumentException("weeks must be between 1 and 104");
        }
        if (stockService.getStockById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(candleService.getWeeklyCandles(id, weeks));
    }
}
