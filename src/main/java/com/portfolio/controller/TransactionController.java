package com.portfolio.controller;

import com.portfolio.dto.TransactionRequest;
import com.portfolio.dto.TransactionResponse;
import com.portfolio.service.TransactionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolio")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);
    private static final Long DEFAULT_PORTFOLIO_ID = 1L;

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * POST /api/portfolio/transactions — 买入/卖出操作
     * 请求体: { "stockId": 1, "type": "BUY", "quantity": 10 }
     * 
     * 验证规则：
     * - BUY: 总价 ≤ 当前现金余额
     * - SELL: 卖出数量 ≤ 持有数量
     */
    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> executeTransaction(
            @Valid @RequestBody TransactionRequest request) {
        log.info("Transaction request: type={}, stockId={}, quantity={}",
                request.type(), request.stockId(), request.quantity());
        TransactionResponse response = transactionService.executeTransaction(DEFAULT_PORTFOLIO_ID, request);
        return ResponseEntity.ok(response);
    }
}
