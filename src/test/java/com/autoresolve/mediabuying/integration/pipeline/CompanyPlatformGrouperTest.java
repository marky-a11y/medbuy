package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.eventbus.EventBus;
import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.messaging.dto.CompanyPlatformMappingMessage;
import com.autoresolve.mediabuying.messaging.dto.SourceSectorMappingMessage;
import com.autoresolve.mediabuying.model.entity.Company;
import com.autoresolve.mediabuying.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CompanyPlatformGrouper}.
 * <p>
 * Verifies that {@code sector.grouped} events are correctly processed,
 * companies are upserted into the database, and {@code company.grouped}
 * events are published.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class CompanyPlatformGrouperTest {

    @Mock
    private CompanyPlatformMapper companyPlatformMapper;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private EventBus eventBus;

    @Captor
    private ArgumentCaptor<CompanyPlatformMappingMessage> mappingCaptor;

    @Captor
    private ArgumentCaptor<Company> companyCaptor;

    private CompanyPlatformGrouper grouper;

    @BeforeEach
    void setUp() {
        grouper = new CompanyPlatformGrouper(companyPlatformMapper, companyRepository, eventBus);
    }

    // ---------------------------------------------------------------
    // 1. Single company mapping → one company.grouped event + DB upsert
    // ---------------------------------------------------------------
    @Test
    void testSingleMappingPublishesOneEventAndUpserts() {
        // Arrange
        SourceSectorMappingMessage sectorMsg = new SourceSectorMappingMessage();
        sectorMsg.setSourceName("test_source");
        sectorMsg.setMatchedSectors(Collections.singletonList("technology"));

        IntegrationEvent event = new IntegrationEvent("sector.grouped", "tech-001", sectorMsg);

        CompanyPlatformMappingMessage mapping = new CompanyPlatformMappingMessage();
        mapping.setCompanyName("TechCorp");
        mapping.setSectorName("technology");
        mapping.setInferredAdPlatforms(Collections.singletonList("linkedin_ads"));
        mapping.setMappingMethod("HEURISTIC");
        mapping.setConfidenceScore(0.75);

        when(companyPlatformMapper.map(sectorMsg)).thenReturn(Collections.singletonList(mapping));
        when(companyRepository.findByCompanyNameAndSectorId("TechCorp", 1L))
                .thenReturn(Optional.empty());

        // Act
        grouper.handleSectorGrouped(event);

        // Assert — verify DB upsert (new company created)
        verify(companyRepository).save(companyCaptor.capture());
        Company saved = companyCaptor.getValue();
        assertEquals("TechCorp", saved.getCompanyName());
        assertEquals(Long.valueOf(1L), saved.getSectorId());
        assertEquals("linkedin_ads", saved.getPrimaryPlatform());
        assertEquals(0, BigDecimal.valueOf(0.75).compareTo(saved.getConfidence()));
        assertTrue(saved.getIsActive());

        // Assert — verify event published
        verify(eventBus, times(1)).publish(eq("company.grouped"), eq("TechCorp"), any());
        verify(eventBus).publish(eq("company.grouped"), eq("TechCorp"), mappingCaptor.capture());
        CompanyPlatformMappingMessage published = mappingCaptor.getValue();
        assertEquals(event.getId(), published.getSourceSectorEventId(),
                "sourceSectorEventId should link back to the IntegrationEvent ID");
    }

    // ---------------------------------------------------------------
    // 2. Multiple company mappings → multiple events + upserts
    // ---------------------------------------------------------------
    @Test
    void testMultipleMappingsPublishMultipleEvents() {
        // Arrange
        SourceSectorMappingMessage sectorMsg = new SourceSectorMappingMessage();
        sectorMsg.setSourceName("multi_source");
        sectorMsg.setMatchedSectors(Collections.singletonList("retail"));

        IntegrationEvent event = new IntegrationEvent("sector.grouped", "multi-001", sectorMsg);

        CompanyPlatformMappingMessage mapping1 = new CompanyPlatformMappingMessage();
        mapping1.setCompanyName("ShopOne");
        mapping1.setSectorName("retail");
        mapping1.setInferredAdPlatforms(Arrays.asList("google_shopping", "meta_ads"));
        mapping1.setMappingMethod("HEURISTIC");
        mapping1.setConfidenceScore(0.85);

        CompanyPlatformMappingMessage mapping2 = new CompanyPlatformMappingMessage();
        mapping2.setCompanyName("ShopTwo");
        mapping2.setSectorName("retail");
        mapping2.setInferredAdPlatforms(Arrays.asList("google_shopping", "meta_ads"));
        mapping2.setMappingMethod("HEURISTIC");
        mapping2.setConfidenceScore(0.85);

        when(companyPlatformMapper.map(sectorMsg))
                .thenReturn(Arrays.asList(mapping1, mapping2));
        when(companyRepository.findByCompanyNameAndSectorId("ShopOne", 4L))
                .thenReturn(Optional.empty());
        when(companyRepository.findByCompanyNameAndSectorId("ShopTwo", 4L))
                .thenReturn(Optional.empty());

        // Act
        grouper.handleSectorGrouped(event);

        // Assert
        verify(companyRepository, times(2)).save(any(Company.class));
        verify(eventBus, times(1)).publish(eq("company.grouped"), eq("ShopOne"), any());
        verify(eventBus, times(1)).publish(eq("company.grouped"), eq("ShopTwo"), any());
        verify(eventBus, times(2)).publish(anyString(), anyString(), any());
    }

    // ---------------------------------------------------------------
    // 3. Error isolation: one company fails, rest continue
    // ---------------------------------------------------------------
    @Test
    void testErrorIsolationWhenOneCompanyFails() {
        // Arrange
        SourceSectorMappingMessage sectorMsg = new SourceSectorMappingMessage();
        sectorMsg.setSourceName("error_source");
        sectorMsg.setMatchedSectors(Collections.singletonList("finance"));

        IntegrationEvent event = new IntegrationEvent("sector.grouped", "err-001", sectorMsg);

        CompanyPlatformMappingMessage goodMapping = new CompanyPlatformMappingMessage();
        goodMapping.setCompanyName("GoodCo");
        goodMapping.setSectorName("finance");
        goodMapping.setInferredAdPlatforms(Collections.singletonList("linkedin_ads"));
        goodMapping.setMappingMethod("HEURISTIC");
        goodMapping.setConfidenceScore(0.70);

        CompanyPlatformMappingMessage badMapping = new CompanyPlatformMappingMessage();
        badMapping.setCompanyName("BadCo");
        badMapping.setSectorName("finance");
        badMapping.setInferredAdPlatforms(Collections.singletonList("linkedin_ads"));
        badMapping.setMappingMethod("HEURISTIC");
        badMapping.setConfidenceScore(0.70);

        when(companyPlatformMapper.map(sectorMsg))
                .thenReturn(Arrays.asList(goodMapping, badMapping));

        // Simulate: first upsert succeeds, second throws
        when(companyRepository.findByCompanyNameAndSectorId("GoodCo", 2L))
                .thenReturn(Optional.empty());
        when(companyRepository.findByCompanyNameAndSectorId("BadCo", 2L))
                .thenThrow(new RuntimeException("DB error"));

        // Act — should not propagate exception
        grouper.handleSectorGrouped(event);

        // Assert — both were attempted (first succeeds for save, second fails)
        verify(companyRepository, times(1)).save(any(Company.class));
        // The good one should have been published
        verify(eventBus, atLeastOnce()).publish(eq("company.grouped"), eq("GoodCo"), any());
    }

    // ---------------------------------------------------------------
    // 4. Null payload in IntegrationEvent → no events
    // ---------------------------------------------------------------
    @Test
    void testNullPayloadNoEvents() {
        // Arrange
        IntegrationEvent event = new IntegrationEvent("sector.grouped", "null-test", null);

        // Act
        grouper.handleSectorGrouped(event);

        // Assert
        verify(companyPlatformMapper, never()).map(any());
        verify(companyRepository, never()).save(any());
        verify(eventBus, never()).publish(anyString(), anyString(), any());
    }

    // ---------------------------------------------------------------
    // 5. No mappings from mapper → no events
    // ---------------------------------------------------------------
    @Test
    void testNoMappingsProducesNoEvents() {
        // Arrange
        SourceSectorMappingMessage sectorMsg = new SourceSectorMappingMessage();
        sectorMsg.setSourceName("empty_source");
        sectorMsg.setMatchedSectors(Collections.singletonList("travel"));

        IntegrationEvent event = new IntegrationEvent("sector.grouped", "empty-001", sectorMsg);

        when(companyPlatformMapper.map(sectorMsg))
                .thenReturn(Collections.<CompanyPlatformMappingMessage>emptyList());

        // Act
        grouper.handleSectorGrouped(event);

        // Assert
        verify(companyRepository, never()).save(any());
        verify(eventBus, never()).publish(anyString(), anyString(), any());
    }

    // ---------------------------------------------------------------
    // 6. Mapper exception → gracefully handled, no events
    // ---------------------------------------------------------------
    @Test
    void testMapperExceptionHandledGracefully() {
        // Arrange
        SourceSectorMappingMessage sectorMsg = new SourceSectorMappingMessage();
        sectorMsg.setSourceName("crash_source");
        sectorMsg.setMatchedSectors(Collections.singletonList("health-wellness"));

        IntegrationEvent event = new IntegrationEvent("sector.grouped", "crash-001", sectorMsg);

        when(companyPlatformMapper.map(sectorMsg))
                .thenThrow(new RuntimeException("Mapper crash"));

        // Act — should not propagate exception
        grouper.handleSectorGrouped(event);

        // Assert
        verify(companyRepository, never()).save(any());
        verify(eventBus, never()).publish(anyString(), anyString(), any());
    }

    // ---------------------------------------------------------------
    // 7. Upsert updates existing company with higher confidence
    // ---------------------------------------------------------------
    @Test
    void testUpsertUpdatesExistingWhenConfidenceIsHigher() {
        // Arrange
        SourceSectorMappingMessage sectorMsg = new SourceSectorMappingMessage();
        sectorMsg.setSourceName("existing_source");
        sectorMsg.setMatchedSectors(Collections.singletonList("technology"));

        IntegrationEvent event = new IntegrationEvent("sector.grouped", "existing-001", sectorMsg);

        CompanyPlatformMappingMessage mapping = new CompanyPlatformMappingMessage();
        mapping.setCompanyName("ExistingCo");
        mapping.setSectorName("technology");
        mapping.setInferredAdPlatforms(Collections.singletonList("linkedin_ads"));
        mapping.setMappingMethod("HEURISTIC");
        mapping.setConfidenceScore(0.90);

        Company existingCompany = Company.builder()
                .id(100L)
                .companyName("ExistingCo")
                .sectorId(1L)
                .primaryPlatform("google_ads")
                .confidence(BigDecimal.valueOf(0.50))
                .isActive(true)
                .build();

        when(companyPlatformMapper.map(sectorMsg))
                .thenReturn(Collections.singletonList(mapping));
        when(companyRepository.findByCompanyNameAndSectorId("ExistingCo", 1L))
                .thenReturn(Optional.of(existingCompany));

        // Act
        grouper.handleSectorGrouped(event);

        // Assert — confidence should be updated to higher value
        verify(companyRepository).save(companyCaptor.capture());
        Company updated = companyCaptor.getValue();
        assertEquals(0, BigDecimal.valueOf(0.90).compareTo(updated.getConfidence()),
                "Confidence should be updated to the higher value");
        assertEquals("linkedin_ads", updated.getPrimaryPlatform(),
                "Primary platform should be updated from the mapping");
    }
}
