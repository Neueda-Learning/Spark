package com.portfolio.scheduler;

import com.portfolio.service.MarketDataSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MarketDataEntryPointProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(MarketDataSyncService.class, () -> mock(MarketDataSyncService.class))
            .withUserConfiguration(MarketDataScheduler.class, MarketDataBackfillRunner.class)
            .withPropertyValues(
                    "market-data.sync.enabled=true",
                    "market-data.backfill-on-startup=true"
            );

    @Test
    void loadsMarketDataEntryPointsInDefaultProfile() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MarketDataScheduler.class);
            assertThat(context).hasSingleBean(MarketDataBackfillRunner.class);
        });
    }

    @Test
    void excludesMarketDataEntryPointsInDemoProfile() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("demo"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MarketDataScheduler.class);
                    assertThat(context).doesNotHaveBean(MarketDataBackfillRunner.class);
                });
    }

    @Test
    void excludesMarketDataEntryPointsInTestProfile() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MarketDataScheduler.class);
                    assertThat(context).doesNotHaveBean(MarketDataBackfillRunner.class);
                });
    }
}
