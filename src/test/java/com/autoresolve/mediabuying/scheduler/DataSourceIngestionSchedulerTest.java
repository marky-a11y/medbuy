package com.autoresolve.mediabuying.scheduler;

import com.autoresolve.mediabuying.eventbus.EventBus;
import com.autoresolve.mediabuying.integration.dto.PipelineBatchCompleteEvent;
import com.autoresolve.mediabuying.integration.dto.RawSourceData;
import com.autoresolve.mediabuying.integration.pipeline.SourceDataNormalizer;
import com.autoresolve.mediabuying.integration.wrapper.BingWebmasterWrapper;
import com.autoresolve.mediabuying.integration.wrapper.EbayApiWrapper;
import com.autoresolve.mediabuying.integration.wrapper.FoursquarePlacesWrapper;
import com.autoresolve.mediabuying.integration.wrapper.JobMarketApiWrapper;
import com.autoresolve.mediabuying.integration.wrapper.MetaAdsLibraryWrapper;
import com.autoresolve.mediabuying.integration.wrapper.PytrendsApiWrapper;
import com.autoresolve.mediabuying.integration.wrapper.RedditApiWrapper;
import com.autoresolve.mediabuying.integration.wrapper.SkyscannerApiWrapper;
import com.autoresolve.mediabuying.integration.wrapper.XApiWrapper;
import com.autoresolve.mediabuying.integration.wrapper.YelpFusionApiWrapper;
import com.autoresolve.mediabuying.messaging.dto.NormalizedSourceMessage;
import com.autoresolve.mediabuying.repository.IngestionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataSourceIngestionScheduler}.
 * <p>
 * Verifies that all 10 wrappers are invoked, normalized messages are
 * published to {@code source.raw}, a single {@code pipeline.batch-complete}
 * event is published per cycle, and failures are handled gracefully.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class DataSourceIngestionSchedulerTest {

    @Mock
    private PytrendsApiWrapper pytrendsWrapper;

    @Mock
    private EbayApiWrapper ebayWrapper;

    @Mock
    private RedditApiWrapper redditWrapper;

    @Mock
    private XApiWrapper xApiWrapper;

    @Mock
    private MetaAdsLibraryWrapper metaAdsLibraryWrapper;

    @Mock
    private YelpFusionApiWrapper yelpFusionWrapper;

    @Mock
    private FoursquarePlacesWrapper foursquarePlacesWrapper;

    @Mock
    private BingWebmasterWrapper bingWebmasterWrapper;

    @Mock
    private SkyscannerApiWrapper skyscannerWrapper;

    @Mock
    private JobMarketApiWrapper jobMarketWrapper;

    @Mock
    private SourceDataNormalizer normalizer;

    @Mock
    private EventBus eventBus;

    @Mock
    private IngestionLogRepository ingestionLogRepository;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    /**
     * Executor backed by a cached thread pool for async execution.
     * Using a real thread pool ensures {@code CompletableFuture.allOf()}
     * works correctly in Java 8.
     */
    private Executor testExecutor;

    private DataSourceIngestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        testExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "test-executor-");
            t.setDaemon(true);
            return t;
        });
        scheduler = new DataSourceIngestionScheduler(
                pytrendsWrapper, ebayWrapper, redditWrapper,
                xApiWrapper, metaAdsLibraryWrapper, yelpFusionWrapper,
                foursquarePlacesWrapper, bingWebmasterWrapper,
                skyscannerWrapper, jobMarketWrapper,
                normalizer, eventBus, testExecutor,
                ingestionLogRepository);
    }

    @Test
    void testIngestAllSourcesAllSucceed() throws Exception {
        // Arrange – all 10 wrappers return mock data
        mockAllWrappersToReturnData();
        mockNormalizerForAllSources();

        // Act
        scheduler.ingestAllSources();

        // Allow any async completions (self-invocation runs synchronously,
        // but the @Async proxy is bypassed, so all work is done inline)
        Thread.sleep(300);

        // Assert – all 10 wrappers were called
        verify(pytrendsWrapper).fetchTrends(anyString(), anyString());
        verify(ebayWrapper).fetchListings(anyString(), anyString());
        verify(redditWrapper).fetchSubredditPosts(anyString(), anyString(), anyInt());
        verify(xApiWrapper).fetchRecentTweets(anyString(), anyInt());
        verify(metaAdsLibraryWrapper).fetchAds(anyString(), anyString());
        verify(yelpFusionWrapper).fetchBusinesses(anyString(), anyString());
        verify(foursquarePlacesWrapper).fetchVenues(anyString(), anyString());
        verify(bingWebmasterWrapper).fetchSearchTraffic(anyString());
        verify(skyscannerWrapper).fetchFlightPrices(anyString(), anyString(), anyString());
        verify(jobMarketWrapper).fetchJobListings(anyString(), anyString());

        // Verify event bus – 10 source.raw + 1 pipeline.batch-complete = 11 total
        verify(eventBus, times(11)).publish(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());

        // Verify source.raw events
        long sourceRawCount = topicCaptor.getAllValues().stream()
                .filter(t -> "source.raw".equals(t))
                .count();
        assertEquals(10, sourceRawCount, "Expected 10 source.raw events");

        // Verify pipeline.batch-complete event
        long batchCompleteCount = topicCaptor.getAllValues().stream()
                .filter(t -> "pipeline.batch-complete".equals(t))
                .count();
        assertEquals(1, batchCompleteCount, "Expected 1 pipeline.batch-complete event");

        // Verify batch-complete payload
        Object batchPayload = eventCaptor.getAllValues().stream()
                .filter(p -> p instanceof PipelineBatchCompleteEvent)
                .findFirst()
                .orElse(null);
        assertNotNull(batchPayload, "pipeline.batch-complete payload must not be null");
        PipelineBatchCompleteEvent batchEvent = (PipelineBatchCompleteEvent) batchPayload;
        assertEquals(10, batchEvent.getTotalSources());
        assertEquals(10, batchEvent.getSuccessfulSources());
        assertTrue(batchEvent.getFailedSources().isEmpty(),
                "No sources should have failed when all return data");
        assertNotNull(batchEvent.getBatchId());
        assertNotNull(batchEvent.getCycleTimestamp());
    }

    @Test
    void testOneWrapperFailsOthersStillSucceed() throws Exception {
        // Arrange – Pytrends fails, all others succeed
        when(pytrendsWrapper.fetchTrends(anyString(), anyString()))
                .thenThrow(new RuntimeException("Pytrends unavailable"));
        mockWrappersExceptPytrendsToReturnData();
        mockNormalizerForAllSourcesExceptPytrends();

        // Act
        scheduler.ingestAllSources();

        // Allow async completions
        Thread.sleep(300);

        // Assert – all 10 wrappers were called
        verify(pytrendsWrapper).fetchTrends(anyString(), anyString());
        verify(ebayWrapper).fetchListings(anyString(), anyString());
        verify(redditWrapper).fetchSubredditPosts(anyString(), anyString(), anyInt());
        verify(xApiWrapper).fetchRecentTweets(anyString(), anyInt());
        verify(metaAdsLibraryWrapper).fetchAds(anyString(), anyString());
        verify(yelpFusionWrapper).fetchBusinesses(anyString(), anyString());
        verify(foursquarePlacesWrapper).fetchVenues(anyString(), anyString());
        verify(bingWebmasterWrapper).fetchSearchTraffic(anyString());
        verify(skyscannerWrapper).fetchFlightPrices(anyString(), anyString(), anyString());
        verify(jobMarketWrapper).fetchJobListings(anyString(), anyString());

        // 9 source.raw + 1 batch-complete = 10 events
        verify(eventBus, times(10)).publish(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());

        long sourceRawCount = topicCaptor.getAllValues().stream()
                .filter(t -> "source.raw".equals(t))
                .count();
        assertEquals(9, sourceRawCount, "Expected 9 source.raw events (1 failed)");

        // Verify batch-complete payload
        Object batchPayload = eventCaptor.getAllValues().stream()
                .filter(p -> p instanceof PipelineBatchCompleteEvent)
                .findFirst()
                .orElse(null);
        assertNotNull(batchPayload);
        PipelineBatchCompleteEvent batchEvent = (PipelineBatchCompleteEvent) batchPayload;
        assertEquals(10, batchEvent.getTotalSources());
        assertEquals(9, batchEvent.getSuccessfulSources());
        assertTrue(batchEvent.getFailedSources().contains("pytrends"),
                "Failed sources list should contain 'pytrends'");
    }

    @Test
    void testNullResponseFromWrapperIsTreatedAsFailure() throws Exception {
        // Arrange – Ebay returns null (not an exception)
        when(pytrendsWrapper.fetchTrends(anyString(), anyString())).thenReturn(createMockRawData("pytrends"));
        when(ebayWrapper.fetchListings(anyString(), anyString())).thenReturn(null);
        when(redditWrapper.fetchSubredditPosts(anyString(), anyString(), anyInt())).thenReturn(createMockRawData("reddit"));
        when(xApiWrapper.fetchRecentTweets(anyString(), anyInt())).thenReturn(createMockRawData("x_api"));
        when(metaAdsLibraryWrapper.fetchAds(anyString(), anyString())).thenReturn(createMockRawData("meta_ads_library"));
        when(yelpFusionWrapper.fetchBusinesses(anyString(), anyString())).thenReturn(createMockRawData("yelp_fusion"));
        when(foursquarePlacesWrapper.fetchVenues(anyString(), anyString())).thenReturn(createMockRawData("foursquare_places"));
        when(bingWebmasterWrapper.fetchSearchTraffic(anyString())).thenReturn(createMockRawData("bing_webmaster"));
        when(skyscannerWrapper.fetchFlightPrices(anyString(), anyString(), anyString())).thenReturn(createMockRawData("skyscanner"));
        when(jobMarketWrapper.fetchJobListings(anyString(), anyString())).thenReturn(createMockRawData("job_market"));

        // Normalizer for all non-null sources
        when(normalizer.normalize(any(RawSourceData.class)))
                .thenAnswer(invocation -> {
                    RawSourceData rd = invocation.getArgument(0);
                    NormalizedSourceMessage msg = new NormalizedSourceMessage();
                    msg.setEventId(UUID.randomUUID().toString());
                    msg.setSourceName(rd.getSourceName());
                    msg.setIngestionTimestamp(Instant.now());
                    return msg;
                });

        // Act
        scheduler.ingestAllSources();

        // Allow async completions
        Thread.sleep(300);

        // 9 source.raw + 1 batch-complete = 10 events
        verify(eventBus, times(10)).publish(anyString(), anyString(), any());

        // Verify batch-complete
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(10)).publish(anyString(), anyString(), payloadCaptor.capture());

        PipelineBatchCompleteEvent batchEvent = (PipelineBatchCompleteEvent) payloadCaptor.getAllValues().stream()
                .filter(p -> p instanceof PipelineBatchCompleteEvent)
                .findFirst()
                .orElse(null);
        assertNotNull(batchEvent);
        assertEquals(9, batchEvent.getSuccessfulSources());
        assertTrue(batchEvent.getFailedSources().contains("ebay"),
                "ebay should be listed as failed because it returned null");
    }

    @Test
    void testAllWrappersReturnNullProducesNoSourceEvents() throws Exception {
        // Arrange – all wrappers return null
        when(pytrendsWrapper.fetchTrends(anyString(), anyString())).thenReturn(null);
        when(ebayWrapper.fetchListings(anyString(), anyString())).thenReturn(null);
        when(redditWrapper.fetchSubredditPosts(anyString(), anyString(), anyInt())).thenReturn(null);
        when(xApiWrapper.fetchRecentTweets(anyString(), anyInt())).thenReturn(null);
        when(metaAdsLibraryWrapper.fetchAds(anyString(), anyString())).thenReturn(null);
        when(yelpFusionWrapper.fetchBusinesses(anyString(), anyString())).thenReturn(null);
        when(foursquarePlacesWrapper.fetchVenues(anyString(), anyString())).thenReturn(null);
        when(bingWebmasterWrapper.fetchSearchTraffic(anyString())).thenReturn(null);
        when(skyscannerWrapper.fetchFlightPrices(anyString(), anyString(), anyString())).thenReturn(null);
        when(jobMarketWrapper.fetchJobListings(anyString(), anyString())).thenReturn(null);

        // Act
        scheduler.ingestAllSources();

        // Allow async completions
        Thread.sleep(300);

        // Only the batch-complete event should be published
        verify(eventBus, times(1)).publish(anyString(), anyString(), any());
        verify(eventBus, times(1)).publish(eq("pipeline.batch-complete"), anyString(), any());

        // Validate batch-complete payload
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).publish(eq("pipeline.batch-complete"), anyString(), payloadCaptor.capture());
        PipelineBatchCompleteEvent batchEvent = (PipelineBatchCompleteEvent) payloadCaptor.getValue();
        assertEquals(0, batchEvent.getSuccessfulSources());
        assertEquals(10, batchEvent.getTotalSources());
        assertEquals(10, batchEvent.getFailedSources().size(),
                "All 10 sources should be in the failed list");
    }

    @Test
    void testNormalizerReturnsNullDoesNotPublishSourceEvent() throws Exception {
        // Arrange – all wrappers return data, but normalizer returns null for all
        mockAllWrappersToReturnData();
        when(normalizer.normalize(any(RawSourceData.class))).thenReturn(null);

        // Act
        scheduler.ingestAllSources();

        // Allow async completions
        Thread.sleep(300);

        // Only 1 batch-complete event (no source.raw events)
        verify(eventBus, times(1)).publish(anyString(), anyString(), any());
        verify(eventBus, never()).publish(eq("source.raw"), anyString(), any());
    }

    @Test
    void testBatchCompleteEventHasValidUuid() throws Exception {
        // Arrange
        mockAllWrappersToReturnData();
        mockNormalizerForAllSources();

        // Act
        scheduler.ingestAllSources();

        // Allow async completions
        Thread.sleep(300);

        // Capture the batch-complete payload
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(11)).publish(anyString(), anyString(), payloadCaptor.capture());

        PipelineBatchCompleteEvent batchEvent = (PipelineBatchCompleteEvent) payloadCaptor.getAllValues().stream()
                .filter(p -> p instanceof PipelineBatchCompleteEvent)
                .findFirst()
                .orElse(null);
        assertNotNull(batchEvent);
        assertNotNull(batchEvent.getBatchId());
        assertDoesNotThrow(() -> UUID.fromString(batchEvent.getBatchId().toString()),
                "batchId must be a valid UUID");
    }

    @Test
    void testBatchCompleteEventCycleTimestampIsRecent() throws Exception {
        // Arrange
        mockAllWrappersToReturnData();
        mockNormalizerForAllSources();

        Instant beforeTest = Instant.now();

        // Act
        scheduler.ingestAllSources();

        // Allow async completions
        Thread.sleep(300);

        Instant afterTest = Instant.now();

        // Capture the batch-complete payload
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(11)).publish(anyString(), anyString(), payloadCaptor.capture());

        PipelineBatchCompleteEvent batchEvent = (PipelineBatchCompleteEvent) payloadCaptor.getAllValues().stream()
                .filter(p -> p instanceof PipelineBatchCompleteEvent)
                .findFirst()
                .orElse(null);
        assertNotNull(batchEvent);
        assertNotNull(batchEvent.getCycleTimestamp());
        assertFalse(batchEvent.getCycleTimestamp().isBefore(beforeTest),
                "cycleTimestamp should be after test started");
        assertFalse(batchEvent.getCycleTimestamp().isAfter(afterTest),
                "cycleTimestamp should be before test ended");
    }

    @Test
    void testAllWrappersThrowExceptionsAllFail() throws Exception {
        // Arrange – every wrapper throws
        when(pytrendsWrapper.fetchTrends(anyString(), anyString()))
                .thenThrow(new RuntimeException("Fail"));
        when(ebayWrapper.fetchListings(anyString(), anyString()))
                .thenThrow(new RuntimeException("Fail"));
        when(redditWrapper.fetchSubredditPosts(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("Fail"));
        when(xApiWrapper.fetchRecentTweets(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Fail"));
        when(metaAdsLibraryWrapper.fetchAds(anyString(), anyString()))
                .thenThrow(new RuntimeException("Fail"));
        when(yelpFusionWrapper.fetchBusinesses(anyString(), anyString()))
                .thenThrow(new RuntimeException("Fail"));
        when(foursquarePlacesWrapper.fetchVenues(anyString(), anyString()))
                .thenThrow(new RuntimeException("Fail"));
        when(bingWebmasterWrapper.fetchSearchTraffic(anyString()))
                .thenThrow(new RuntimeException("Fail"));
        when(skyscannerWrapper.fetchFlightPrices(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Fail"));
        when(jobMarketWrapper.fetchJobListings(anyString(), anyString()))
                .thenThrow(new RuntimeException("Fail"));

        // Act
        scheduler.ingestAllSources();

        // Allow async completions
        Thread.sleep(300);

        // Only 1 batch-complete event
        verify(eventBus, times(1)).publish(anyString(), anyString(), any());
        verify(eventBus, never()).publish(eq("source.raw"), anyString(), any());

        // Verify batch-complete stats
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).publish(eq("pipeline.batch-complete"), anyString(), payloadCaptor.capture());
        PipelineBatchCompleteEvent batchEvent = (PipelineBatchCompleteEvent) payloadCaptor.getValue();
        assertEquals(0, batchEvent.getSuccessfulSources());
        assertEquals(10, batchEvent.getFailedSources().size());
    }

    // ========== Helper methods ==========

    private void mockAllWrappersToReturnData() {
        when(pytrendsWrapper.fetchTrends(anyString(), anyString())).thenReturn(createMockRawData("pytrends"));
        when(ebayWrapper.fetchListings(anyString(), anyString())).thenReturn(createMockRawData("ebay"));
        when(redditWrapper.fetchSubredditPosts(anyString(), anyString(), anyInt())).thenReturn(createMockRawData("reddit"));
        when(xApiWrapper.fetchRecentTweets(anyString(), anyInt())).thenReturn(createMockRawData("x_api"));
        when(metaAdsLibraryWrapper.fetchAds(anyString(), anyString())).thenReturn(createMockRawData("meta_ads_library"));
        when(yelpFusionWrapper.fetchBusinesses(anyString(), anyString())).thenReturn(createMockRawData("yelp_fusion"));
        when(foursquarePlacesWrapper.fetchVenues(anyString(), anyString())).thenReturn(createMockRawData("foursquare_places"));
        when(bingWebmasterWrapper.fetchSearchTraffic(anyString())).thenReturn(createMockRawData("bing_webmaster"));
        when(skyscannerWrapper.fetchFlightPrices(anyString(), anyString(), anyString())).thenReturn(createMockRawData("skyscanner"));
        when(jobMarketWrapper.fetchJobListings(anyString(), anyString())).thenReturn(createMockRawData("job_market"));
    }

    private void mockWrappersExceptPytrendsToReturnData() {
        when(ebayWrapper.fetchListings(anyString(), anyString())).thenReturn(createMockRawData("ebay"));
        when(redditWrapper.fetchSubredditPosts(anyString(), anyString(), anyInt())).thenReturn(createMockRawData("reddit"));
        when(xApiWrapper.fetchRecentTweets(anyString(), anyInt())).thenReturn(createMockRawData("x_api"));
        when(metaAdsLibraryWrapper.fetchAds(anyString(), anyString())).thenReturn(createMockRawData("meta_ads_library"));
        when(yelpFusionWrapper.fetchBusinesses(anyString(), anyString())).thenReturn(createMockRawData("yelp_fusion"));
        when(foursquarePlacesWrapper.fetchVenues(anyString(), anyString())).thenReturn(createMockRawData("foursquare_places"));
        when(bingWebmasterWrapper.fetchSearchTraffic(anyString())).thenReturn(createMockRawData("bing_webmaster"));
        when(skyscannerWrapper.fetchFlightPrices(anyString(), anyString(), anyString())).thenReturn(createMockRawData("skyscanner"));
        when(jobMarketWrapper.fetchJobListings(anyString(), anyString())).thenReturn(createMockRawData("job_market"));
    }

    private void mockNormalizerForAllSources() {
        when(normalizer.normalize(any(RawSourceData.class)))
                .thenAnswer(invocation -> {
                    RawSourceData rd = invocation.getArgument(0);
                    NormalizedSourceMessage msg = new NormalizedSourceMessage();
                    msg.setEventId(UUID.randomUUID().toString());
                    msg.setSourceName(rd.getSourceName());
                    msg.setSourceUrl(rd.getSourceUrl());
                    msg.setSourceType(rd.getSourceType());
                    msg.setRawData(rd.getRawPayload());
                    msg.setNormalizedSummary(rd.getNormalizedSummary());
                    msg.setIngestionTimestamp(rd.getFetchTimestamp());
                    msg.setIngestionKey(rd.getIngestionKey());
                    return msg;
                });
    }

    private void mockNormalizerForAllSourcesExceptPytrends() {
        when(normalizer.normalize(any(RawSourceData.class)))
                .thenAnswer(invocation -> {
                    RawSourceData rd = invocation.getArgument(0);
                    NormalizedSourceMessage msg = new NormalizedSourceMessage();
                    msg.setEventId(UUID.randomUUID().toString());
                    msg.setSourceName(rd.getSourceName());
                    msg.setIngestionTimestamp(Instant.now());
                    return msg;
                });
    }

    private RawSourceData createMockRawData(String sourceName) {
        return RawSourceData.builder()
                .sourceName(sourceName)
                .sourceUrl("https://example.com/" + sourceName)
                .sourceType("MOCK")
                .rawPayload("{\"mock\":true}")
                .normalizedSummary(sourceName + " data: 10 records")
                .recordCount(10)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(sourceName + "_" + System.currentTimeMillis())
                .licenseType("PUBLIC")
                .build();
    }
}
