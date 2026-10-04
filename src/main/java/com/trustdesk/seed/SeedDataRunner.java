package com.trustdesk.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Loads seed data at startup. Disable with {@code trustdesk.seed.enabled=false}. A bad seed fails startup. */
@Component
@ConditionalOnProperty(prefix = "trustdesk.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeedDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);
    private final SeedDataService seedDataService;

    public SeedDataRunner(SeedDataService seedDataService) {
        this.seedDataService = seedDataService;
    }

    @Override
    public void run(ApplicationArguments args) {
        SeedSummary s = seedDataService.load();
        log.info("Seed data loaded: {}", s);
    }
}
