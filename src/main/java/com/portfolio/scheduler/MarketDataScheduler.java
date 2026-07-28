package com.portfolio.scheduler;

import com.portfolio.service.MarketDataSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo & !test")
@ConditionalOnProperty(
        name = "market-data.sync.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MarketDataScheduler {

    private final MarketDataSyncService marketDataSyncService;

    public MarketDataScheduler(MarketDataSyncService marketDataSyncService) {
        this.marketDataSyncService = marketDataSyncService;
    }

    @Scheduled(
            cron = "${market-data.sync.post-close-cron:0 30 18 * * MON-FRI}",
            zone = "${market-data.sync.zone:America/New_York}"
    )
    public void syncAfterClose() {
        marketDataSyncService.syncAll();
    }

    @Scheduled(
            cron = "${market-data.sync.reconciliation-cron:0 0 8 * * MON-FRI}",
            zone = "${market-data.sync.zone:America/New_York}"
    )
    public void reconcileBeforeOpen() {
        marketDataSyncService.syncAll();
    }
}
