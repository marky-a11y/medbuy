package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.eventbus.EventBus;
import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.messaging.dto.NormalizedSourceMessage;
import com.autoresolve.mediabuying.messaging.dto.SourceSectorMappingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage 2 event listener that consumes {@code source.raw} events, classifies
 * the source into business sectors via {@link SectorClassifier}, and publishes
 * each matching sector as a {@code sector.grouped} event on the event bus.
 * <p>
 * This listener runs <strong>synchronously</strong> within the publisher's
 * wrapper thread (no {@code @Async}). Error handling is per-sector: if
 * publishing fails for one sector, the remaining sectors are still processed.
 * </p>
 */
@Component
public class SectorGrouper {

    private static final Logger log = LoggerFactory.getLogger(SectorGrouper.class);

    private final SectorClassifier sectorClassifier;
    private final EventBus eventBus;

    public SectorGrouper(SectorClassifier sectorClassifier, EventBus eventBus) {
        this.sectorClassifier = sectorClassifier;
        this.eventBus = eventBus;
    }

    /**
     * Handle a {@code source.raw} event: classify the source message and
     * publish one {@code sector.grouped} event per matched sector.
     *
     * @param event the integration event carrying a {@link NormalizedSourceMessage} payload
     */
    @EventListener(condition = "#event.topic == 'source.raw'")
    public void handleSourceRaw(IntegrationEvent event) {
        if (event == null) {
            log.warn("Received null IntegrationEvent on topic 'source.raw'");
            return;
        }

        Object payload = event.getPayload();
        if (!(payload instanceof NormalizedSourceMessage)) {
            log.warn("Unexpected payload type on topic 'source.raw': {} — expected NormalizedSourceMessage",
                    payload != null ? payload.getClass().getName() : "null");
            return;
        }

        NormalizedSourceMessage msg = (NormalizedSourceMessage) payload;
        String sourceName = msg.getSourceName();
        String sourceEventId = msg.getEventId();

        log.info("Processing source.raw: source='{}' eventId='{}'", sourceName, sourceEventId);

        List<SourceSectorMappingMessage> mappings;
        try {
            mappings = sectorClassifier.classify(msg);
        } catch (Exception e) {
            log.error("Sector classification failed for source '{}': {}", sourceName, e.getMessage(), e);
            return;
        }

        if (mappings == null || mappings.isEmpty()) {
            log.info("No sector classification for source '{}'", sourceName);
            return;
        }

        List<String> publishedSectors = new ArrayList<>();

        for (SourceSectorMappingMessage mapping : mappings) {
            try {
                // Link back to the raw source event
                mapping.setRawEventId(event.getId());
                mapping.setProcessingTimestamp(Instant.now());

                String sectorName = mapping.getMatchedSectors().get(0);

                log.debug("Publishing sector.grouped: source='{}' sector='{}' confidence={}",
                        sourceName, sectorName, mapping.getConfidenceScore());

                eventBus.publish("sector.grouped", sectorName, mapping);
                publishedSectors.add(sectorName);
            } catch (Exception e) {
                log.error("Failed to publish sector.grouped for source '{}' sector(s) '{}': {}",
                        sourceName, mapping.getMatchedSectors(), e.getMessage(), e);
                // Continue processing remaining sectors — error isolation per spec
            }
        }

        log.info("Classified source '{}' into {} sector(s): {}",
                sourceName, publishedSectors.size(), publishedSectors);
    }
}
