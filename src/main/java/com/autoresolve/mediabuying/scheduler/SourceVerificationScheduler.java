package com.autoresolve.mediabuying.scheduler;

import com.autoresolve.mediabuying.service.SourceAttributionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that verifies the availability of data source URLs
 * by sending periodic HTTP HEAD requests to each stale data source.
 * Runs daily at 2:00 AM by default.
 */
@Component
public class SourceVerificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SourceVerificationScheduler.class);

    private final SourceAttributionService sourceAttributionService;

    public SourceVerificationScheduler(SourceAttributionService sourceAttributionService) {
        this.sourceAttributionService = sourceAttributionService;
    }

    /**
     * Daily verification of stale data source URLs.
     * Cron expression: daily at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void verifyStaleSources() {
        log.info("Starting scheduled source URL verification");
        sourceAttributionService.verifySourceUrls();
        log.info("Scheduled source URL verification complete");
    }
}
