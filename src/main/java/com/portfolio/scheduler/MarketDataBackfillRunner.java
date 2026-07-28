package com.portfolio.scheduler;

import com.portfolio.service.MarketDataSyncService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo & !test")
@ConditionalOnProperty(
        name = "market-data.backfill-on-startup",
        havingValue = "true"
)
public class MarketDataBackfillRunner implements ApplicationRunner {

    private final MarketDataSyncService marketDataSyncService;

    public MarketDataBackfillRunner(MarketDataSyncService marketDataSyncService) {
        this.marketDataSyncService = marketDataSyncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        marketDataSyncService.syncAll();
    }
}
