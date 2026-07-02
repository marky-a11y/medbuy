package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.dto.SourceMetadataDTO;
import com.autoresolve.mediabuying.model.entity.DataSource;
import com.autoresolve.mediabuying.model.entity.KpiSourceAttribution;
import com.autoresolve.mediabuying.repository.DataSourceRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.KpiSourceAttributionRepository;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SourceAttributionServiceTest {

    @Mock
    private DataSourceRepository dataSourceRepository;

    @Mock
    private KpiSourceAttributionRepository kpiSourceAttributionRepository;

    @Mock
    private KPIMetricsRepository kpiMetricsRepository;

    @Mock
    private Counter staleSourceCounter;

    @Captor
    private ArgumentCaptor<Long> kpiMetricsIdCaptor;

    @Captor
    private ArgumentCaptor<Long> dataSourceIdCaptor;

    @Captor
    private ArgumentCaptor<String> contextCaptor;

    private SourceAttributionService service;

    @BeforeEach
    void setUp() {
        service = new SourceAttributionService(
                dataSourceRepository, kpiSourceAttributionRepository, kpiMetricsRepository, staleSourceCounter);
    }

    // --- linkKpiToSources tests ---

    @Test
    void testLinkKpiToSourcesHappyPath() {
        Long kpiMetricsId = 1L;
        List<String> sourceNames = Collections.singletonList("Google Ads API v17");
        String context = "RAW";

        DataSource ds = DataSource.builder()
                .id(10L)
                .sourceName("Google Ads API v17")
                .build();

        when(dataSourceRepository.findBySourceName("Google Ads API v17"))
                .thenReturn(Optional.of(ds));

        service.linkKpiToSources(kpiMetricsId, sourceNames, context);

        verify(kpiSourceAttributionRepository).upsert(1L, 10L, "RAW");
    }

    @Test
    void testLinkKpiToSourcesUnknownSourceLogsWarning() {
        Long kpiMetricsId = 2L;
        List<String> sourceNames = Collections.singletonList("Unknown Source");
        String context = "RAW";

        when(dataSourceRepository.findBySourceName("Unknown Source"))
                .thenReturn(Optional.empty());

        // Should not throw
        service.linkKpiToSources(kpiMetricsId, sourceNames, context);

        verify(kpiSourceAttributionRepository, never()).upsert(anyLong(), anyLong(), anyString());
    }

    @Test
    void testLinkKpiToSourcesEmptyListDoesNothing() {
        service.linkKpiToSources(3L, Collections.emptyList(), "RAW");
        service.linkKpiToSources(4L, null, "RAW");

        verify(kpiSourceAttributionRepository, never()).upsert(anyLong(), anyLong(), anyString());
    }

    @Test
    void testLinkKpiToSourcesMultipleSources() {
        Long kpiMetricsId = 5L;
        List<String> sourceNames = Arrays.asList("Google Ads API v17", "Meta Marketing API");
        String context = "RAW";

        DataSource googleDs = DataSource.builder().id(10L).sourceName("Google Ads API v17").build();
        DataSource metaDs = DataSource.builder().id(20L).sourceName("Meta Marketing API").build();

        when(dataSourceRepository.findBySourceName("Google Ads API v17")).thenReturn(Optional.of(googleDs));
        when(dataSourceRepository.findBySourceName("Meta Marketing API")).thenReturn(Optional.of(metaDs));

        service.linkKpiToSources(kpiMetricsId, sourceNames, context);

        verify(kpiSourceAttributionRepository).upsert(5L, 10L, "RAW");
        verify(kpiSourceAttributionRepository).upsert(5L, 20L, "RAW");
    }

    @Test
    void testLinkKpiToSourcesDuplicateLinkNoError() {
        Long kpiMetricsId = 6L;
        List<String> sourceNames = Collections.singletonList("Google Ads API v17");
        String context = "RAW";

        DataSource ds = DataSource.builder().id(10L).sourceName("Google Ads API v17").build();
        when(dataSourceRepository.findBySourceName("Google Ads API v17")).thenReturn(Optional.of(ds));

        // Call twice - should not throw due to ON CONFLICT DO NOTHING
        service.linkKpiToSources(kpiMetricsId, sourceNames, context);
        service.linkKpiToSources(kpiMetricsId, sourceNames, context);

        verify(kpiSourceAttributionRepository, times(2)).upsert(6L, 10L, "RAW");
    }

    // --- getSourcesForKpi tests ---

    @Test
    void testGetSourcesForKpiReturnsCorrectDTOs() {
        Long kpiMetricsId = 10L;

        KpiSourceAttribution ksa = KpiSourceAttribution.builder()
                .id(100L)
                .kpiMetricsId(10L)
                .dataSourceId(200L)
                .attributionContext("RAW")
                .build();

        DataSource ds = DataSource.builder()
                .id(200L)
                .sourceName("Google Ads API v17")
                .sourceType("API")
                .sourceUrl("https://developers.google.com/google-ads/api")
                .licenseType("PROPRIETARY")
                .lastVerifiedAt(Instant.now())
                .build();

        when(kpiSourceAttributionRepository.findByKpiMetricsId(10L))
                .thenReturn(Collections.singletonList(ksa));
        when(dataSourceRepository.findById(200L))
                .thenReturn(Optional.of(ds));

        List<SourceMetadataDTO> result = service.getSourcesForKpi(kpiMetricsId);

        assertNotNull(result);
        assertEquals(1, result.size());
        SourceMetadataDTO dto = result.get(0);
        assertEquals("Google Ads API v17", dto.getSourceName());
        assertEquals("API", dto.getSourceType());
        assertEquals("PROPRIETARY", dto.getLicenseType());
        assertFalse(dto.isStale());
        assertEquals("RAW", dto.getAttributionContext());
    }

    @Test
    void testGetSourcesForKpiWithStaleSource() {
        Long kpiMetricsId = 11L;

        KpiSourceAttribution ksa = KpiSourceAttribution.builder()
                .id(101L)
                .kpiMetricsId(11L)
                .dataSourceId(201L)
                .attributionContext("RAW")
                .build();

        // lastVerifiedAt is null -> isStale should be true
        DataSource ds = DataSource.builder()
                .id(201L)
                .sourceName("Old Source")
                .sourceType("API")
                .sourceUrl("https://example.com")
                .licenseType("PUBLIC")
                .lastVerifiedAt(null)
                .build();

        when(kpiSourceAttributionRepository.findByKpiMetricsId(11L))
                .thenReturn(Collections.singletonList(ksa));
        when(dataSourceRepository.findById(201L))
                .thenReturn(Optional.of(ds));

        List<SourceMetadataDTO> result = service.getSourcesForKpi(kpiMetricsId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isStale());
    }

    @Test
    void testGetSourcesForKpiWithSourceOlderThan30Days() {
        Long kpiMetricsId = 12L;

        KpiSourceAttribution ksa = KpiSourceAttribution.builder()
                .id(102L)
                .kpiMetricsId(12L)
                .dataSourceId(202L)
                .attributionContext("RAW")
                .build();

        // lastVerifiedAt = 40 days ago -> isStale should be true
        DataSource ds = DataSource.builder()
                .id(202L)
                .sourceName("Stale Source")
                .sourceType("REPORT")
                .sourceUrl("https://example.com/report")
                .licenseType("OPEN")
                .lastVerifiedAt(Instant.now().minus(40, ChronoUnit.DAYS))
                .build();

        when(kpiSourceAttributionRepository.findByKpiMetricsId(12L))
                .thenReturn(Collections.singletonList(ksa));
        when(dataSourceRepository.findById(202L))
                .thenReturn(Optional.of(ds));

        List<SourceMetadataDTO> result = service.getSourcesForKpi(kpiMetricsId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isStale());
    }

    @Test
    void testGetSourcesForKpiEmptyReturnsEmptyList() {
        when(kpiSourceAttributionRepository.findByKpiMetricsId(99L))
                .thenReturn(Collections.emptyList());

        List<SourceMetadataDTO> result = service.getSourcesForKpi(99L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // --- verifySourceUrls tests ---

    @Test
    void testVerifySourceUrlsNoStaleSourcesDoesNothing() {
        when(dataSourceRepository.findStaleSources(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        service.verifySourceUrls();

        verify(dataSourceRepository, never()).save(any(DataSource.class));
        verify(staleSourceCounter, never()).increment();
    }

    @Test
    void testVerifySourceUrlsWithStaleSourceHandlesException() {
        DataSource staleDs = DataSource.builder()
                .id(1L)
                .sourceName("Test Source")
                .sourceUrl("https://example.com/api")
                .sourceType("API")
                .licenseType("PROPRIETARY")
                .lastVerifiedAt(null)
                .build();

        when(dataSourceRepository.findStaleSources(any(Instant.class)))
                .thenReturn(Collections.singletonList(staleDs));

        // The HTTP connection will fail since there's no real server, which tests the exception path
        service.verifySourceUrls();

        // lastVerifiedAt should remain null (not updated on failure)
        assertNull(staleDs.getLastVerifiedAt());
        // Counter should be incremented on failure
        verify(staleSourceCounter, atLeastOnce()).increment();
    }

    @Test
    void testVerifySourceUrlsMultipleStaleSources() {
        DataSource staleDs1 = DataSource.builder()
                .id(1L)
                .sourceName("Source 1")
                .sourceUrl("https://example.com/api1")
                .sourceType("API")
                .licenseType("PROPRIETARY")
                .lastVerifiedAt(null)
                .build();

        DataSource staleDs2 = DataSource.builder()
                .id(2L)
                .sourceName("Source 2")
                .sourceUrl("https://example.com/api2")
                .sourceType("API")
                .licenseType("PUBLIC")
                .lastVerifiedAt(null)
                .build();

        when(dataSourceRepository.findStaleSources(any(Instant.class)))
                .thenReturn(Arrays.asList(staleDs1, staleDs2));

        service.verifySourceUrls();

        // Both should fail (no real HTTP server) and counter incremented
        assertNull(staleDs1.getLastVerifiedAt());
        assertNull(staleDs2.getLastVerifiedAt());
        verify(staleSourceCounter, atLeastOnce()).increment();
    }
}
