package com.autoresolve.mediabuying.scheduler;

import com.autoresolve.mediabuying.eventbus.EventBus;
import com.autoresolve.mediabuying.integration.dto.PipelineBatchCompleteEvent;
import com.autoresolve.mediabuying.integration.dto.RawSourceData;
import com.autoresolve.mediabuying.integration.pipeline.SourceDataNormalizer;
import com.autoresolve.mediabuying.integration.wrapper.BaseApiWrapper;
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
import com.autoresolve.mediabuying.model.entity.IngestionLog;
import com.autoresolve.mediabuying.repository.IngestionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * Scheduled task that fires all 10 DSRC-03 market-data source wrappers in
 * parallel, normalises each response, and publishes {@link NormalizedSourceMessage}
 * events to the {@code source.raw} event-bus topic.
 * <p>
 * At the end of every cycle a {@link PipelineBatchCompleteEvent} is sent to
 * {@code pipeline.batch-complete} with aggregate statistics.
 * </p>
 * <p>
 * Runs every 15 minutes by default ({@code pipeline.ingestion.interval-ms}).
 * Replaces the old 5-platform pipeline (removed in DSRC-09).
 * </p>
 */
@Component
public class DataSourceIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataSourceIngestionScheduler.class);

    private static final int TOTAL_SOURCES = 10;

    private final PytrendsApiWrapper pytrendsWrapper;
    private final EbayApiWrapper ebayWrapper;
    private final RedditApiWrapper redditWrapper;
    private final XApiWrapper xApiWrapper;
    private final MetaAdsLibraryWrapper metaAdsLibraryWrapper;
    private final YelpFusionApiWrapper yelpFusionWrapper;
    private final FoursquarePlacesWrapper foursquarePlacesWrapper;
    private final BingWebmasterWrapper bingWebmasterWrapper;
    private final SkyscannerApiWrapper skyscannerWrapper;
    private final JobMarketApiWrapper jobMarketWrapper;
    private final SourceDataNormalizer normalizer;
    private final EventBus eventBus;
    private final Executor executor;
    private final IngestionLogRepository ingestionLogRepository;

    @Value("${pipeline.ingestion.timeout-seconds:600}")
    private int timeoutSeconds = 600;

    public DataSourceIngestionScheduler(
            PytrendsApiWrapper pytrendsWrapper,
            EbayApiWrapper ebayWrapper,
            RedditApiWrapper redditWrapper,
            XApiWrapper xApiWrapper,
            MetaAdsLibraryWrapper metaAdsLibraryWrapper,
            YelpFusionApiWrapper yelpFusionWrapper,
            FoursquarePlacesWrapper foursquarePlacesWrapper,
            BingWebmasterWrapper bingWebmasterWrapper,
            SkyscannerApiWrapper skyscannerWrapper,
            JobMarketApiWrapper jobMarketWrapper,
            SourceDataNormalizer normalizer,
            EventBus eventBus,
            @Qualifier("adPlatformTaskExecutor") Executor executor,
            IngestionLogRepository ingestionLogRepository) {
        this.pytrendsWrapper = pytrendsWrapper;
        this.ebayWrapper = ebayWrapper;
        this.redditWrapper = redditWrapper;
        this.xApiWrapper = xApiWrapper;
        this.metaAdsLibraryWrapper = metaAdsLibraryWrapper;
        this.yelpFusionWrapper = yelpFusionWrapper;
        this.foursquarePlacesWrapper = foursquarePlacesWrapper;
        this.bingWebmasterWrapper = bingWebmasterWrapper;
        this.skyscannerWrapper = skyscannerWrapper;
        this.jobMarketWrapper = jobMarketWrapper;
        this.normalizer = normalizer;
        this.eventBus = eventBus;
        this.executor = executor;
        this.ingestionLogRepository = ingestionLogRepository;
        System.out.println("=== PHASE: DataSourceIngestionScheduler constructor completed at " + System.currentTimeMillis() + " ===");
    }

    @PostConstruct
    public void init() {
        System.out.println("=== PHASE: DataSourceIngestionScheduler @PostConstruct at " + System.currentTimeMillis() + " ===");
    }

    /**
     * Scheduled ingestion of all 10 market-data sources.
     * Default: runs every 15 minutes (900,000 ms).
     */
    @Scheduled(
        fixedRateString = "${pipeline.ingestion.interval-ms:900000}",
        initialDelayString = "${pipeline.ingestion.initial-delay-ms:300000}")
    public void ingestAllSources() {
        UUID cycleId = UUID.randomUUID();
        log.info("Starting scheduled source ingestion (cycle={}) for all {} market-data wrappers",
                cycleId, TOTAL_SOURCES);

        // Thread-safe tracking: stores RawSourceData for successful fetches, null for failures
        final ConcurrentMap<String, RawSourceData> results = new ConcurrentHashMap<>();
        final List<String> failedSources = Collections.synchronizedList(new ArrayList<String>());

        // Invoke all 10 wrappers sequentially on the current thread.
        // Parallelism will be reintroduced in a follow-up story once the
        // threading infrastructure is properly tested in isolation.
        fetchAndPublish("pytrends", pytrendsWrapper, results, failedSources, cycleId);
        fetchAndPublish("ebay", ebayWrapper, results, failedSources, cycleId);
        fetchAndPublish("reddit", redditWrapper, results, failedSources, cycleId);
        fetchAndPublish("x_api", xApiWrapper, results, failedSources, cycleId);
        fetchAndPublish("meta_ads_library", metaAdsLibraryWrapper, results, failedSources, cycleId);
        fetchAndPublish("yelp_fusion", yelpFusionWrapper, results, failedSources, cycleId);
        fetchAndPublish("foursquare_places", foursquarePlacesWrapper, results, failedSources, cycleId);
        fetchAndPublish("bing_webmaster", bingWebmasterWrapper, results, failedSources, cycleId);
        fetchAndPublish("skyscanner", skyscannerWrapper, results, failedSources, cycleId);
        fetchAndPublish("job_market", jobMarketWrapper, results, failedSources, cycleId);

        log.info("All {} source wrappers completed", TOTAL_SOURCES);

        // Count how many sources returned usable data
        int successfulSources = 0;
        for (Map.Entry<String, RawSourceData> entry : results.entrySet()) {
            if (entry.getValue() != null) {
                successfulSources++;
            }
        }

        // Build and publish the batch-complete event once per cycle
        PipelineBatchCompleteEvent batchEvent = PipelineBatchCompleteEvent.builder()
                .batchId(UUID.randomUUID())
                .totalSources(TOTAL_SOURCES)
                .successfulSources(successfulSources)
                .failedSources(new ArrayList<String>(failedSources))
                .cycleTimestamp(Instant.now())
                .build();

        eventBus.publish("pipeline.batch-complete", "batch", batchEvent);

        log.info("Source ingestion cycle complete: {}/{} sources successful, {} failed",
                successfulSources, TOTAL_SOURCES, failedSources.size());
    }

    /**
     * Fetches data from a single source wrapper and publishes the normalized
     * message. Called from the executor's thread. Exceptions are caught so
     * one failure does not block other sources.
     */
    private void fetchAndPublish(String sourceName,
                                  BaseApiWrapper<RawSourceData> wrapper,
                                  ConcurrentMap<String, RawSourceData> results,
                                  List<String> failedSources,
                                  UUID cycleId) {
        Instant now = Instant.now();
        IngestionLog.IngestionLogBuilder logEntry = IngestionLog.builder()
                .cycleId(cycleId)
                .sourceName(sourceName)
                .ingestionTimestamp(now);

        try {
            log.debug("Fetching data for source={}", sourceName);

            RawSourceData rawData = callWrapper(sourceName, wrapper);

            if (rawData == null) {
                log.warn("Source={} returned null response, skipping", sourceName);
                failedSources.add(sourceName);
                logEntry.fetchStatus("FAILED").errorMessage("Wrapper returned null");
                ingestionLogRepository.save(logEntry.build());
                return;
            }

            NormalizedSourceMessage normalizedMsg = normalizer.normalize(rawData);
            if (normalizedMsg == null) {
                log.warn("Normalizer returned null for source={}, skipping", sourceName);
                failedSources.add(sourceName);
                logEntry.fetchStatus("FAILED").errorMessage("Normalizer returned null");
                ingestionLogRepository.save(logEntry.build());
                return;
            }

            eventBus.publish("source.raw", sourceName, normalizedMsg);
            results.put(sourceName, rawData);
            log.info("Successfully ingested data for source={}", sourceName);

            // Persist ingestion log
            logEntry.fetchStatus(rawData.getFetchStatus())
                    .sourceType(rawData.getSourceType())
                    .recordCount(rawData.getRecordCount())
                    .normalizedSummary(rawData.getNormalizedSummary());
            ingestionLogRepository.save(logEntry.build());

        } catch (Exception e) {
            log.error("Unexpected error ingesting source={}: {}", sourceName, e.getMessage(), e);
            failedSources.add(sourceName);
            logEntry.fetchStatus("ERROR").errorMessage(truncate(e.getMessage(), 500));
            ingestionLogRepository.save(logEntry.build());
        }
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    /**
     * Dispatches to the appropriate wrapper method based on the source name.
     * Each wrapper has a unique method signature, so we use a simple switch.
     */
    private RawSourceData callWrapper(String sourceName,
                                       BaseApiWrapper<RawSourceData> wrapper) {
        if ("pytrends".equals(sourceName)) {
            return ((PytrendsApiWrapper) wrapper).fetchTrends("digital marketing", "US");
        } else if ("ebay".equals(sourceName)) {
            return ((EbayApiWrapper) wrapper).fetchListings("digital marketing", "9355");
        } else if ("reddit".equals(sourceName)) {
            return ((RedditApiWrapper) wrapper).fetchSubredditPosts("marketing", "hot", 25);
        } else if ("x_api".equals(sourceName)) {
            return ((XApiWrapper) wrapper).fetchRecentTweets("digital marketing -is:retweet", 30);
        } else if ("meta_ads_library".equals(sourceName)) {
            return ((MetaAdsLibraryWrapper) wrapper).fetchAds("digital marketing", "US");
        } else if ("yelp_fusion".equals(sourceName)) {
            return ((YelpFusionApiWrapper) wrapper).fetchBusinesses("marketing", "San Francisco, CA");
        } else if ("foursquare_places".equals(sourceName)) {
            return ((FoursquarePlacesWrapper) wrapper).fetchVenues("New York, NY", "retail");
        } else if ("bing_webmaster".equals(sourceName)) {
            return ((BingWebmasterWrapper) wrapper).fetchSearchTraffic("");
        } else if ("skyscanner".equals(sourceName)) {
            return ((SkyscannerApiWrapper) wrapper).fetchFlightPrices("JFK", "LAX", "2026-08-15");
        } else if ("job_market".equals(sourceName)) {
            return ((JobMarketApiWrapper) wrapper).fetchJobListings("software engineer", "New York");
        }
        log.warn("Unknown source name: {}", sourceName);
        return null;
    }
}
