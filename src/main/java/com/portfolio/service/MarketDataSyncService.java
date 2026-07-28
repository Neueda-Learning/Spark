package com.portfolio.service;

public interface MarketDataSyncService {

    SyncSummary syncAll();

    record SyncSummary(int stocksProcessed, int recordsUpserted, int recordsSkipped, int stocksFailed) {
    }
}
