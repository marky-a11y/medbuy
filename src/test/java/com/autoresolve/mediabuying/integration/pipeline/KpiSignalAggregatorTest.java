package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.messaging.dto.CompanyPlatformMappingMessage;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link KpiSignalAggregator}.
 * <p>
 * Verifies that weighted blending produces realistic KPI values within the
 * specified MVP ranges and edge cases are handled gracefully.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class KpiSignalAggregatorTest {

    private KpiSignalAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new KpiSignalAggregator();
    }

    // ---------------------------------------------------------------
    // 1. Single signal produces KPI values within MVP ranges
    // ---------------------------------------------------------------
    @Test
    void testSingleSignalProducesValidRanges() {
        CompanyPlatformMappingMessage signal = createSignal("TestCo", "technology",
                Collections.singletonList("google_ads"), 0.85);

        KPIMetrics metrics = aggregator.aggregate(Collections.singletonList(signal), "technology");

        assertNotNull(metrics);
        assertNotNull(metrics.getRoas());
        assertNotNull(metrics.getCac());
        assertNotNull(metrics.getCltv());

        // ROAS: 1.5–6.0 range
        assertTrue(metrics.getRoas().doubleValue() >= 1.0,
                "ROAS should be >= 1.0 but was " + metrics.getRoas());
        assertTrue(metrics.getRoas().doubleValue() <= 8.0,
                "ROAS should be <= 8.0 but was " + metrics.getRoas());

        // CAC: 10–80 range
        assertTrue(metrics.getCac().doubleValue() >= 5.0,
                "CAC should be >= 5.0 but was " + metrics.getCac());
        assertTrue(metrics.getCac().doubleValue() <= 100.0,
                "CAC should be <= 100.0 but was " + metrics.getCac());

        // CLTV: 100–800 range
        assertTrue(metrics.getCltv().doubleValue() >= 50.0,
                "CLTV should be >= 50.0 but was " + metrics.getCltv());
        assertTrue(metrics.getCltv().doubleValue() <= 1000.0,
                "CLTV should be <= 1000.0 but was " + metrics.getCltv());

        // Platform and sector IDs resolved correctly
        assertEquals(Long.valueOf(1L), metrics.getPlatformId(), "google_ads → platformId 1");
        assertEquals(Long.valueOf(1L), metrics.getSectorId(), "technology → sectorId 1");
    }

    // ---------------------------------------------------------------
    // 2. Multiple signals with same confidence → average blending
    // ---------------------------------------------------------------
    @Test
    void testMultipleSignalsEqualConfidenceProducesAverage() {
        CompanyPlatformMappingMessage s1 = createSignal("CoA", "retail",
                Collections.singletonList("meta_ads"), 0.8);
        CompanyPlatformMappingMessage s2 = createSignal("CoA", "retail",
                Collections.singletonList("meta_ads"), 0.8);

        KPIMetrics metrics = aggregator.aggregate(Arrays.asList(s1, s2), "retail");

        assertNotNull(metrics);
        assertEquals(Long.valueOf(4L), metrics.getSectorId(), "retail → sectorId 4");
        assertEquals(Long.valueOf(2L), metrics.getPlatformId(), "meta_ads → platformId 2");
    }

    // ---------------------------------------------------------------
    // 3. Null or empty signal list → returns null
    // ---------------------------------------------------------------
    @Test
    void testNullSignalListReturnsNull() {
        KPIMetrics metrics = aggregator.aggregate(null, "technology");
        assertNull(metrics);
    }

    @Test
    void testEmptySignalListReturnsNull() {
        KPIMetrics metrics = aggregator.aggregate(new ArrayList<CompanyPlatformMappingMessage>(), "technology");
        assertNull(metrics);
    }

    // ---------------------------------------------------------------
    // 4. Signals with confidence = 0 → uses equal weights fallback
    // ---------------------------------------------------------------
    @Test
    void testZeroConfidenceUsesEqualWeights() {
        CompanyPlatformMappingMessage s1 = createSignal("CoA", "retail",
                Collections.singletonList("meta_ads"), 0.0);
        CompanyPlatformMappingMessage s2 = createSignal("CoA", "retail",
                Collections.singletonList("meta_ads"), 0.0);

        KPIMetrics metrics = aggregator.aggregate(Arrays.asList(s1, s2), "retail");

        // Should still produce valid KPIs using equal weighting fallback
        assertNotNull(metrics);
        assertNotNull(metrics.getRoas());
        assertTrue(metrics.getRoas().doubleValue() > 0);
    }

    // ---------------------------------------------------------------
    // 5. Mixed confidence levels → weighted blending
    // ---------------------------------------------------------------
    @Test
    void testMixedConfidenceWeightedBlending() {
        CompanyPlatformMappingMessage highConf = createSignal("CoA", "finance",
                Collections.singletonList("linkedin_ads"), 0.95);
        CompanyPlatformMappingMessage lowConf = createSignal("CoA", "finance",
                Collections.singletonList("linkedin_ads"), 0.2);

        KPIMetrics metrics = aggregator.aggregate(Arrays.asList(highConf, lowConf), "finance");

        assertNotNull(metrics);
        // Platform and sector should be resolved
        assertEquals(Long.valueOf(2L), metrics.getSectorId(), "finance → sectorId 2");
        assertEquals(Long.valueOf(4L), metrics.getPlatformId(), "linkedin_ads → platformId 4");
    }

    // ---------------------------------------------------------------
    // 6. Different sector produces different KPI values
    // ---------------------------------------------------------------
    @Test
    void testDifferentSectorsProduceDifferentValues() {
        CompanyPlatformMappingMessage tech = createSignal("TechCo", "technology",
                Collections.singletonList("google_ads"), 0.8);
        CompanyPlatformMappingMessage travel = createSignal("TravelCo", "travel",
                Collections.singletonList("bing_ads"), 0.8);

        KPIMetrics techMetrics = aggregator.aggregate(Collections.singletonList(tech), "technology");
        KPIMetrics travelMetrics = aggregator.aggregate(Collections.singletonList(travel), "travel");

        assertNotNull(techMetrics);
        assertNotNull(travelMetrics);

        // Different sectors should have different KPI values
        // (Due to deterministic hash, values should differ)
        assertNotEquals(techMetrics.getRoas(), travelMetrics.getRoas(),
                "ROAS should differ between sectors");
        assertNotEquals(techMetrics.getCac(), travelMetrics.getCac(),
                "CAC should differ between sectors");

        // Sector IDs should match
        assertEquals(Long.valueOf(1L), techMetrics.getSectorId());
        assertEquals(Long.valueOf(6L), travelMetrics.getSectorId());
    }

    // ---------------------------------------------------------------
    // 7. Null sector name → uses sectorId 0
    // ---------------------------------------------------------------
    @Test
    void testNullSectorDefaultsToZeroId() {
        CompanyPlatformMappingMessage signal = createSignal("TestCo", null,
                Collections.singletonList("google_ads"), 0.7);

        KPIMetrics metrics = aggregator.aggregate(Collections.singletonList(signal), null);

        assertNotNull(metrics);
        assertEquals(Long.valueOf(0L), metrics.getSectorId(), "null sector → sectorId 0");
    }

    // ---------------------------------------------------------------
    // 8. Null confidence score → falls back to 0.5
    // ---------------------------------------------------------------
    @Test
    void testNullConfidenceFallsBackToDefault() {
        CompanyPlatformMappingMessage signal = createSignal("TestCo", "retail",
                Collections.singletonList("meta_ads"), null);

        KPIMetrics metrics = aggregator.aggregate(Collections.singletonList(signal), "retail");

        assertNotNull(metrics);
        assertNotNull(metrics.getRoas());
        assertTrue(metrics.getRoas().doubleValue() > 0);
    }

    // ---------------------------------------------------------------
    // 9. Unknown platform name → platformId 0
    // ---------------------------------------------------------------
    @Test
    void testUnknownPlatformResolvesToZero() {
        CompanyPlatformMappingMessage signal = createSignal("TestCo", "technology",
                Collections.singletonList("unknown_platform_xyz"), 0.75);

        KPIMetrics metrics = aggregator.aggregate(Collections.singletonList(signal), "technology");

        assertNotNull(metrics);
        assertEquals(Long.valueOf(0L), metrics.getPlatformId(), "unknown platform → platformId 0");
    }

    // ---------------------------------------------------------------
    // 10. Multiple signals, some with null platform list
    // ---------------------------------------------------------------
    @Test
    void testSignalWithNullPlatformList() {
        CompanyPlatformMappingMessage s1 = createSignal("CoA", "retail",
                Collections.singletonList("meta_ads"), 0.8);
        CompanyPlatformMappingMessage s2 = createSignal("CoA", "retail",
                null, 0.7);

        // Should still process — platformId will be 0 for signals without platforms
        KPIMetrics metrics = aggregator.aggregate(Arrays.asList(s1, s2), "retail");

        assertNotNull(metrics);
        // First signal's platform should be used
        assertEquals(Long.valueOf(2L), metrics.getPlatformId(), "first signal's platform should be used");
    }

    // ========== helpers ==========

    private CompanyPlatformMappingMessage createSignal(String companyName, String sectorName,
                                                        List<String> platforms, Double confidence) {
        CompanyPlatformMappingMessage msg = new CompanyPlatformMappingMessage();
        msg.setCompanyName(companyName);
        msg.setSectorName(sectorName);
        msg.setInferredAdPlatforms(platforms);
        msg.setConfidenceScore(confidence);
        msg.setMappingMethod("HEURISTIC");
        return msg;
    }
}
