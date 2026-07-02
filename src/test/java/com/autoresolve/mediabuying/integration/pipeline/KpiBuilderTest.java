package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.eventbus.EventBus;
import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.integration.dto.PipelineBatchCompleteEvent;
import com.autoresolve.mediabuying.messaging.dto.CompanyPlatformMappingMessage;
import com.autoresolve.mediabuying.messaging.dto.KpiRefreshEvent;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.repository.DataSourceRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.KpiSourceAttributionRepository;
import com.autoresolve.mediabuying.service.KpiCsvWriter;
import com.autoresolve.mediabuying.service.OpportunityMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KpiBuilder}.
 * <p>
 * Verifies the two-phase pipeline: synchronous accumulation (Phase 4a)
 * and asynchronous batch processing (Phase 4b) including draining, grouping,
 * aggregation, upsert, and refresh-event publishing.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class KpiBuilderTest {

    @Mock
    private KpiSignalAggregator kpiSignalAggregator;

    @Mock
    private KPIMetricsRepository kpiMetricsRepository;

    @Mock
    private EventBus eventBus;

    @Mock
    private KpiCsvWriter kpiCsvWriter;

    @Mock
    private KpiSourceAttributionRepository kpiSourceAttributionRepository;

    @Mock
    private DataSourceRepository dataSourceRepository;

    @Mock
    private OpportunityMetricsService opportunityMetricsService;

    @Captor
    private ArgumentCaptor<KPIMetrics> kpiMetricsCaptor;

    @Captor
    private ArgumentCaptor<KpiRefreshEvent> refreshEventCaptor;

    private KpiBuilder kpiBuilder;

    @BeforeEach
    void setUp() {
        kpiBuilder = new KpiBuilder(kpiSignalAggregator, kpiMetricsRepository, eventBus, kpiCsvWriter,
                kpiSourceAttributionRepository, dataSourceRepository, opportunityMetricsService);
        // Ensure accumulator is clean before each test
        kpiBuilder.accumulator.clear();
    }

    // ========================================================================
    // Phase 4a: Accumulation tests
    // ========================================================================

    // ---------------------------------------------------------------
    // 1. Single company.grouped event appends to accumulator
    // ---------------------------------------------------------------
    @Test
    void testSingleCompanyGroupedAppendsToAccumulator() {
        CompanyPlatformMappingMessage msg = createMessage("TestCo", "technology",
                Collections.singletonList("google_ads"), 0.85);
        IntegrationEvent event = new IntegrationEvent("company.grouped", "TestCo", msg);

        kpiBuilder.onCompanyGrouped(event);

        assertEquals(1, kpiBuilder.accumulator.size(),
                "Accumulator should have one sector entry");
        List<CompanyPlatformMappingMessage> list = kpiBuilder.accumulator.get("technology");
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("TestCo", list.get(0).getCompanyName());
    }

    // ---------------------------------------------------------------
    // 2. Multiple events for same sector append correctly
    // ---------------------------------------------------------------
    @Test
    void testMultipleEventsForSameSectorAppend() {
        CompanyPlatformMappingMessage msg1 = createMessage("CoA", "technology",
                Collections.singletonList("google_ads"), 0.8);
        CompanyPlatformMappingMessage msg2 = createMessage("CoB", "technology",
                Collections.singletonList("linkedin_ads"), 0.7);

        kpiBuilder.onCompanyGrouped(new IntegrationEvent("company.grouped", "CoA", msg1));
        kpiBuilder.onCompanyGrouped(new IntegrationEvent("company.grouped", "CoB", msg2));

        List<CompanyPlatformMappingMessage> list = kpiBuilder.accumulator.get("technology");
        assertNotNull(list);
        assertEquals(2, list.size());
    }

    // ---------------------------------------------------------------
    // 3. Multiple sectors accumulate separately
    // ---------------------------------------------------------------
    @Test
    void testMultipleSectorsAccumulateSeparately() {
        CompanyPlatformMappingMessage techMsg = createMessage("TechCo", "technology",
                Collections.singletonList("google_ads"), 0.8);
        CompanyPlatformMappingMessage retailMsg = createMessage("RetailCo", "retail",
                Collections.singletonList("meta_ads"), 0.75);

        kpiBuilder.onCompanyGrouped(new IntegrationEvent("company.grouped", "TechCo", techMsg));
        kpiBuilder.onCompanyGrouped(new IntegrationEvent("company.grouped", "RetailCo", retailMsg));

        assertEquals(2, kpiBuilder.accumulator.size());
        assertEquals(1, kpiBuilder.accumulator.get("technology").size());
        assertEquals(1, kpiBuilder.accumulator.get("retail").size());
    }

    // ---------------------------------------------------------------
    // 4. Null sector name → uses "default" key
    // ---------------------------------------------------------------
    @Test
    void testNullSectorUsesDefaultKey() {
        CompanyPlatformMappingMessage msg = createMessage("TestCo", null,
                Collections.singletonList("google_ads"), 0.8);

        kpiBuilder.onCompanyGrouped(new IntegrationEvent("company.grouped", "TestCo", msg));

        assertTrue(kpiBuilder.accumulator.containsKey("default"),
                "Null sector should map to 'default' key");
        assertEquals(1, kpiBuilder.accumulator.get("default").size());
    }

    // ---------------------------------------------------------------
    // 5. Null payload in IntegrationEvent → ignored, no accumulation
    // ---------------------------------------------------------------
    @Test
    void testNullPayloadIgnoresEvent() {
        IntegrationEvent event = new IntegrationEvent("company.grouped", "null-test", null);

        kpiBuilder.onCompanyGrouped(event);

        assertTrue(kpiBuilder.accumulator.isEmpty(),
                "Null payload should not add to accumulator");
    }

    // ========================================================================
    // Phase 4b: Batch-complete processing tests
    // ========================================================================

    // ---------------------------------------------------------------
    // 6. Batch-complete drains accumulator and processes groups
    // ---------------------------------------------------------------
    @Test
    void testBatchCompleteDrainsAccumulatorAndProcesses() {
        // Arrange — pre-populate accumulator with two signals for the same sector
        CompanyPlatformMappingMessage msg1 = createMessage("CoA", "technology",
                Collections.singletonList("google_ads"), 0.8);
        CompanyPlatformMappingMessage msg2 = createMessage("CoB", "technology",
                Collections.singletonList("linkedin_ads"), 0.7);

        kpiBuilder.onCompanyGrouped(new IntegrationEvent("company.grouped", "CoA", msg1));
        kpiBuilder.onCompanyGrouped(new IntegrationEvent("company.grouped", "CoB", msg2));

        // Stub aggregator to return valid KPIMetrics
        KPIMetrics mockMetrics1 = KPIMetrics.builder()
                .platformId(1L).sectorId(1L)
                .roas(java.math.BigDecimal.valueOf(3.5))
                .cac(java.math.BigDecimal.valueOf(35.0))
                .cltv(java.math.BigDecimal.valueOf(350.0))
                .build();
        KPIMetrics mockMetrics2 = KPIMetrics.builder()
                .platformId(3L).sectorId(1L)
                .roas(java.math.BigDecimal.valueOf(2.8))
                .cac(java.math.BigDecimal.valueOf(42.0))
                .cltv(java.math.BigDecimal.valueOf(280.0))
                .build();

        when(kpiSignalAggregator.aggregate(anyList(), eq("technology")))
                .thenReturn(mockMetrics1)   // first group
                .thenReturn(mockMetrics2);  // second group

        PipelineBatchCompleteEvent batchEvent = PipelineBatchCompleteEvent.builder()
                .batchId(UUID.randomUUID())
                .totalSources(10)
                .successfulSources(8)
                .failedSources(Collections.<String>emptyList())
                .cycleTimestamp(java.time.Instant.now())
                .build();
        IntegrationEvent completeEvent = new IntegrationEvent("pipeline.batch-complete", "batch-001", batchEvent);

        // Act
        kpiBuilder.onBatchComplete(completeEvent);

        // Assert — accumulator should be drained
        assertTrue(kpiBuilder.accumulator.isEmpty(),
                "Accumulator should be drained after batch-complete");

        // Assert — aggregator was called for each group
        verify(kpiSignalAggregator, times(2)).aggregate(anyList(), eq("technology"));

        // Assert — upsert called for each KPI
        verify(kpiMetricsRepository, times(2)).upsert(any(KPIMetrics.class));

        // Assert — refresh events published for each unique (platformId, sectorId)
        verify(eventBus, times(2)).publish(eq("kpi.refresh"), anyString(), any(KpiRefreshEvent.class));
    }

    // ---------------------------------------------------------------
    // 7. Empty accumulator on batch-complete → no KPIs, no refresh
    // ---------------------------------------------------------------
    @Test
    void testEmptyAccumulatorOnBatchCompleteProducesNoActions() {
        PipelineBatchCompleteEvent batchEvent = PipelineBatchCompleteEvent.builder()
                .batchId(UUID.randomUUID())
                .totalSources(10)
                .successfulSources(10)
                .failedSources(Collections.<String>emptyList())
                .cycleTimestamp(java.time.Instant.now())
                .build();
        IntegrationEvent completeEvent = new IntegrationEvent("pipeline.batch-complete", "batch-empty", batchEvent);

        kpiBuilder.onBatchComplete(completeEvent);

        verify(kpiSignalAggregator, never()).aggregate(anyList(), anyString());
        verify(kpiMetricsRepository, never()).upsert(any(KPIMetrics.class));
        verify(eventBus, never()).publish(anyString(), anyString(), any());
    }

    // ---------------------------------------------------------------
    // 8. Double batch-complete: first processes, second does nothing
    // ---------------------------------------------------------------
    @Test
    void testDoubleBatchCompleteSecondDoesNothing() {
        // First batch-complete
        CompanyPlatformMappingMessage msg = createMessage("CoA", "retail",
                Collections.singletonList("meta_ads"), 0.8);
        kpiBuilder.onCompanyGrouped(new IntegrationEvent("company.grouped", "CoA", msg));

        KPIMetrics mockMetrics = KPIMetrics.builder()
                .platformId(2L).sectorId(4L)
                .roas(java.math.BigDecimal.valueOf(4.0))
                .cac(java.math.BigDecimal.valueOf(30.0))
                .cltv(java.math.BigDecimal.valueOf(400.0))
                .build();
        when(kpiSignalAggregator.aggregate(anyList(), eq("retail")))
                .thenReturn(mockMetrics);

        PipelineBatchCompleteEvent batchEvent = PipelineBatchCompleteEvent.builder()
                .batchId(UUID.randomUUID())
                .totalSources(10)
                .successfulSources(8)
                .failedSources(Collections.<String>emptyList())
                .cycleTimestamp(java.time.Instant.now())
                .build();

        kpiBuilder.onBatchComplete(new IntegrationEvent("pipeline.batch-complete", "batch-1", batchEvent));

        // Second batch-complete (accumulator already empty)
        kpiBuilder.onBatchComplete(new IntegrationEvent("pipeline.batch-complete", "batch-2", batchEvent));

        // Aggregator should only have been called once (from the first batch-complete)
        verify(kpiSignalAggregator, times(1)).aggregate(anyList(), eq("retail"));
        verify(kpiMetricsRepository, times(1)).upsert(any(KPIMetrics.class));
    }

    // ---------------------------------------------------------------
    // 9. Batch-complete with wrong payload type → no processing
    // ---------------------------------------------------------------
    @Test
    void testBatchCompleteWrongPayloadTypeIgnored() {
        IntegrationEvent wrongEvent = new IntegrationEvent("pipeline.batch-complete",
                "batch-wrong", "this is not a PipelineBatchCompleteEvent");

        kpiBuilder.onBatchComplete(wrongEvent);

        verify(kpiSignalAggregator, never()).aggregate(anyList(), anyString());
        verify(kpiMetricsRepository, never()).upsert(any());
        verify(eventBus, never()).publish(anyString(), anyString(), any());
    }

    // ---------------------------------------------------------------
    // 10. Concurrent accumulation (simulated with two threads)
    // ---------------------------------------------------------------
    @Test
    void testConcurrentAccumulationToSameSector() throws Exception {
        int threadCount = 4;
        int messagesPerThread = 25;
        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < messagesPerThread; i++) {
                    CompanyPlatformMappingMessage msg = createMessage(
                            "Co-" + threadId + "-" + i,
                            "technology",
                            Collections.singletonList("google_ads"),
                            0.5 + (i % 5) * 0.1
                    );
                    IntegrationEvent event = new IntegrationEvent("company.grouped", msg.getCompanyName(), msg);
                    kpiBuilder.onCompanyGrouped(event);
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // All messages should be in the accumulator
        List<CompanyPlatformMappingMessage> list = kpiBuilder.accumulator.get("technology");
        assertNotNull(list);
        assertEquals(threadCount * messagesPerThread, list.size(),
                "All concurrent messages should be accumulated");
    }

    // ---------------------------------------------------------------
    // 11. Null payload in company.grouped → ignored
    // (additional edge case)
    // ---------------------------------------------------------------
    @Test
    void testNullEventOnCompanyGroupedIgnored() {
        kpiBuilder.onCompanyGrouped(null);
        assertTrue(kpiBuilder.accumulator.isEmpty());
    }

    // ---------------------------------------------------------------
    // 12. GroupByCompanyPlatform helper works correctly
    // ---------------------------------------------------------------
    @Test
    void testGroupByCompanyPlatformGroupsCorrectly() {
        CompanyPlatformMappingMessage s1 = createMessage("CoA", "tech",
                Collections.singletonList("google_ads"), 0.8);
        CompanyPlatformMappingMessage s2 = createMessage("CoA", "tech",
                Collections.singletonList("google_ads"), 0.9);
        CompanyPlatformMappingMessage s3 = createMessage("CoB", "tech",
                Collections.singletonList("linkedin_ads"), 0.7);

        List<CompanyPlatformMappingMessage> signals = Arrays.asList(s1, s2, s3);
        Map<String, List<CompanyPlatformMappingMessage>> grouped = KpiBuilder.groupByCompanyPlatform(signals);

        assertEquals(2, grouped.size(), "Should produce 2 groups");

        // CoA + tech + google_ads group should have 2 signals
        String key1 = "CoA::tech::google_ads";
        assertTrue(grouped.containsKey(key1));
        assertEquals(2, grouped.get(key1).size());

        // CoB + tech + linkedin_ads group should have 1 signal
        String key2 = "CoB::tech::linkedin_ads";
        assertTrue(grouped.containsKey(key2));
        assertEquals(1, grouped.get(key2).size());
    }

    // ========== helpers ==========

    private CompanyPlatformMappingMessage createMessage(String companyName, String sectorName,
                                                        List<String> platforms, Double confidence) {
        CompanyPlatformMappingMessage msg = new CompanyPlatformMappingMessage();
        msg.setEventId(UUID.randomUUID().toString());
        msg.setCompanyName(companyName);
        msg.setSectorName(sectorName);
        msg.setInferredAdPlatforms(platforms);
        msg.setConfidenceScore(confidence);
        msg.setMappingMethod("HEURISTIC");
        return msg;
    }
}
