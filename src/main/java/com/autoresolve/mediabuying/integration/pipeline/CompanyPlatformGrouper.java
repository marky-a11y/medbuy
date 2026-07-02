package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.eventbus.EventBus;
import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.messaging.dto.CompanyPlatformMappingMessage;
import com.autoresolve.mediabuying.messaging.dto.SourceSectorMappingMessage;
import com.autoresolve.mediabuying.model.entity.Company;
import com.autoresolve.mediabuying.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stage&#x2083 event listener that consumes {@code sector.grouped} events,
 * extracts company/brand names via {@link CompanyPlatformMapper}, upserts
 * them into the {@code companies} table, and publishes {@code company.grouped}
 * events on the event bus.
 * <p>
 * This listener runs <strong>synchronously</strong> within the publisher's
 * thread (no {@code @Async}). Error handling is per-company: if one mapping
 * fails, the remaining mappings are still processed.
 * </p>
 */
@Component
public class CompanyPlatformGrouper {

    private static final Logger log = LoggerFactory.getLogger(CompanyPlatformGrouper.class);

    private final CompanyPlatformMapper companyPlatformMapper;
    private final CompanyRepository companyRepository;
    private final EventBus eventBus;

    public CompanyPlatformGrouper(CompanyPlatformMapper companyPlatformMapper,
                                  CompanyRepository companyRepository,
                                  EventBus eventBus) {
        this.companyPlatformMapper = companyPlatformMapper;
        this.companyRepository = companyRepository;
        this.eventBus = eventBus;
    }

    /**
     * Handle a {@code sector.grouped} event: extract companies, upsert into
     * database, then publish {@code company.grouped} events.
     *
     * @param event the integration event carrying a {@link SourceSectorMappingMessage} payload
     */
    @EventListener(condition = "#event.topic == 'sector.grouped'")
    public void handleSectorGrouped(IntegrationEvent event) {
        if (event == null) {
            log.warn("Received null IntegrationEvent on topic 'sector.grouped'");
            return;
        }

        Object payload = event.getPayload();
        if (!(payload instanceof SourceSectorMappingMessage)) {
            log.warn("Unexpected payload type on topic 'sector.grouped': {} — expected SourceSectorMappingMessage",
                    payload != null ? payload.getClass().getName() : "null");
            return;
        }

        SourceSectorMappingMessage msg = (SourceSectorMappingMessage) payload;
        String sourceName = msg.getSourceName();
        String sectorName = extractFirstSector(msg);

        log.info("Processing sector.grouped: source='{}' sector='{}'", sourceName, sectorName);

        // Map to company→platform messages
        List<CompanyPlatformMappingMessage> mappings;
        try {
            mappings = companyPlatformMapper.map(msg);
        } catch (Exception e) {
            log.error("Company platform mapping failed for source '{}' sector '{}': {}",
                    sourceName, sectorName, e.getMessage(), e);
            return;
        }

        if (mappings == null || mappings.isEmpty()) {
            log.info("No company mappings for source '{}' sector '{}'", sourceName, sectorName);
            return;
        }

        // Process each mapping: upsert company + publish event
        for (CompanyPlatformMappingMessage mapping : mappings) {
            try {
                // Link back to the source sector event
                mapping.setSourceSectorEventId(event.getId());

                // Upsert company into database
                upsertCompany(mapping);

                // Publish company.grouped event
                String companyName = mapping.getCompanyName();
                log.debug("Publishing company.grouped: company='{}' platform='{}' confidence={}",
                        companyName, mapping.getInferredAdPlatforms(), mapping.getConfidenceScore());

                eventBus.publish("company.grouped", companyName, mapping);
            } catch (Exception e) {
                log.error("Failed to process company mapping for source '{}' company '{}': {}",
                        sourceName, mapping.getCompanyName(), e.getMessage(), e);
                // Continue with remaining mappings — error isolation per spec
            }
        }

        log.info("Mapped source '{}' into {} company grouping(s)", sourceName, mappings.size());
    }

    /**
     * Upsert a company record: find by name+sector, update confidence if higher,
     * otherwise create a new record.
     */
    @Transactional
    void upsertCompany(CompanyPlatformMappingMessage mapping) {
        String companyName = mapping.getCompanyName();

        // Determine sector ID — for MVP we map sector name to a simple hash ID
        // In production this would be resolved via CommerceSectorRepository
        Long sectorId = resolveSectorId(mapping.getSectorName());

        // Try to find existing company
        Optional<Company> existing = companyRepository.findByCompanyNameAndSectorId(companyName, sectorId);

        if (existing.isPresent()) {
            Company company = existing.get();
            BigDecimal newConfidence = BigDecimal.valueOf(mapping.getConfidenceScore());

            // Update confidence only if the new value is higher
            if (company.getConfidence() == null || newConfidence.compareTo(company.getConfidence()) > 0) {
                company.setConfidence(newConfidence);
            }

            // Update primary platform from the first inferred platform
            List<String> platforms = mapping.getInferredAdPlatforms();
            if (platforms != null && !platforms.isEmpty()) {
                company.setPrimaryPlatform(platforms.get(0));
            }

            company.setSourceName(mapping.getSectorName());
            company.setIsActive(true);
            companyRepository.save(company);
            log.trace("Updated existing company '{}' (id={})", companyName, company.getId());
        } else {
            // Create new company
            Company newCompany = Company.builder()
                    .companyName(companyName)
                    .sectorId(sectorId)
                    .primaryPlatform(mapping.getInferredAdPlatforms() != null
                            && !mapping.getInferredAdPlatforms().isEmpty()
                            ? mapping.getInferredAdPlatforms().get(0) : null)
                    .sourceName(mapping.getSectorName())
                    .confidence(BigDecimal.valueOf(mapping.getConfidenceScore()))
                    .isActive(true)
                    .build();

            companyRepository.save(newCompany);
            log.trace("Created new company '{}' (id={})", companyName, newCompany.getId());
        }
    }

    /**
     * Resolve a sector name to a numeric sector ID.
     * <p>
     * For the MVP, we map known sector names to fixed IDs.
     * In production this should look up via {@code CommerceSectorRepository}.
     * </p>
     */
    private Long resolveSectorId(String sectorName) {
        if (sectorName == null) {
            return 0L;
        }
        // Simple deterministic mapping for MVP
        switch (sectorName.toLowerCase().trim()) {
            case "technology":
                return 1L;
            case "finance":
                return 2L;
            case "manufacturing":
                return 3L;
            case "retail":
                return 4L;
            case "health-wellness":
                return 5L;
            case "travel":
                return 6L;
            case "job-market":
                return 7L;
            case "local-business":
                return 8L;
            default:
                return 0L;
        }
    }

    private String extractFirstSector(SourceSectorMappingMessage msg) {
        if (msg.getMatchedSectors() != null && !msg.getMatchedSectors().isEmpty()) {
            return msg.getMatchedSectors().get(0);
        }
        return null;
    }
}
