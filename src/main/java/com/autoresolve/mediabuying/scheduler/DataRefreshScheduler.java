package com.autoresolve.mediabuying.scheduler;

import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.messaging.producer.DataRefreshProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler and event consumer for data refresh operations.
 * <p>
 * Two modes of operation:
 * <ol>
 *   <li><b>Scheduled</b> — Every 5 minutes, sends a {@code REFRESH_ALL} command
 *       to the {@code data-refresh.internal} event bus topic.</li>
 *   <li><b>Event-driven</b> — Consumes {@code REFRESH_ALL} commands from
 *       {@code data-refresh.internal} and delegates to
 *       {@link DataSourceIngestionScheduler#ingestAllSources()}.</li>
 * </ol>
 * </p>
 *
 * <p>All events are delivered in-JVM via Spring Events. No external broker required.</p>
 */
@Component
public class DataRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRefreshScheduler.class);

    private final DataRefreshProducer dataRefreshProducer;
    private final DataSourceIngestionScheduler ingestionScheduler;

    public DataRefreshScheduler(DataRefreshProducer dataRefreshProducer,
                                 DataSourceIngestionScheduler ingestionScheduler) {
        this.dataRefreshProducer = dataRefreshProducer;
        this.ingestionScheduler = ingestionScheduler;
    }

    /**
     * Scheduled task that triggers data refresh every 5 minutes.
     * Uses cron expression to run at fixed intervals.
     */
    @Scheduled(fixedRateString = "${data-refresh.dashboard-interval-minutes:5}000", initialDelay = 30000)
    public void triggerDataRefresh() {
        log.debug("Scheduled data refresh triggered");
        try {
            dataRefreshProducer.sendDataRefreshCommand();
        } catch (Exception e) {
            log.error("Failed to trigger data refresh", e);
        }
    }

    /**
     * Consumes {@code REFRESH_ALL} commands from the {@code data-refresh.internal} event bus topic
     * and triggers the ingestion pipeline.
     */
    @EventListener(condition = "#event.topic == 'data-refresh.internal'")
    public void onRefreshCommand(IntegrationEvent event) {
        Object payload = event.getPayload();
        if (!(payload instanceof String)) {
            log.warn("Unexpected payload type on data-refresh.internal: {}",
                    payload != null ? payload.getClass().getName() : "null");
            return;
        }
        String message = (String) payload;

        log.info("Received data refresh command: {}", message);

        try {
            if (message != null && message.contains("REFRESH_ALL")) {
                log.info("REFRESH_ALL command received; delegating to ingestion scheduler");
                ingestionScheduler.ingestAllSources();
                log.info("Ingestion pipeline completed after REFRESH_ALL command");
            } else {
                log.warn("Unknown data refresh command: {}", message);
            }
        } catch (Exception e) {
            log.error("Failed to process data refresh command", e);
        }
    }
}
