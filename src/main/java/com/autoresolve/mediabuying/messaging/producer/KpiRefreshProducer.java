package com.autoresolve.mediabuying.messaging.producer;

import com.autoresolve.mediabuying.eventbus.EventBus;
import com.autoresolve.mediabuying.messaging.dto.KpiRefreshEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Produces cache-invalidation events to the {@code kpi.refresh} event bus topic.
 *
 * <p>Events are published in-JVM via Spring Events. No external broker required.</p>
 */
@Component
public class KpiRefreshProducer {

    private static final Logger log = LoggerFactory.getLogger(KpiRefreshProducer.class);

    private final EventBus eventBus;

    public KpiRefreshProducer(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void sendKpiRefreshEvent(Long platformId, Long sectorId) {
        KpiRefreshEvent event = KpiRefreshEvent.builder()
                .platformId(platformId)
                .sectorId(sectorId)
                .eventType("KPI_UPDATED")
                .refreshTimestamp(Instant.now())
                .build();

        eventBus.publish("kpi.refresh", platformId.toString(), event);
        log.debug("KpiRefreshEvent published: platform={}, sector={}", platformId, sectorId);
    }
}
