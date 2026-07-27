package com.portfolio.service;

import com.portfolio.dto.TransactionRequest;
import com.portfolio.dto.TransactionResponse;

public interface TransactionService {
    /**
     * Execute a buy or sell transaction.
     * - BUY: validates sufficient cash, updates holding (create or add), deducts cash
     * - SELL: validates sufficient quantity, updates holding (reduce or remove), adds cash
     */
    TransactionResponse executeTransaction(Long portfolioId, TransactionRequest request);
}
