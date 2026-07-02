package com.autoresolve.mediabuying.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Raw source data DTO returned by all 10 market-data source wrappers.
 * <p>
 * Each wrapper in the DSRC-03 set converts its external API response (or mock)
 * into this uniform format for downstream Stage 1 (Source Data Normalizer)
 * processing.
 * </p>
 *
 * @see com.autoresolve.mediabuying.integration.wrapper.BaseApiWrapper
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawSourceData {

    /** Human-readable name of the source, e.g. "yelp_fusion", "pytrends". */
    private String sourceName;

    /** URL of the specific resource that was fetched (or the API base URL). */
    private String sourceUrl;

    /**
     * How the data was obtained.
     * One of: "API", "SCRAPER", "MOCK".
     */
    private String sourceType;

    /** The raw response payload, typically a JSON string. */
    private String rawPayload;

    /** A brief human-readable summary of what was fetched. */
    private String normalizedSummary;

    /** Number of records / items contained in the payload. */
    private int recordCount;

    /**
     * Status of the fetch operation.
     * One of: "SUCCESS", "PARTIAL", "FAILED", "MOCK".
     */
    private String fetchStatus;

    /** When the fetch was performed. */
    private Instant fetchTimestamp;

    /**
     * Unique ingestion key, typically {@code sourceName + "_" + epochMillis}.
     * Used for idempotency checks downstream.
     */
    private String ingestionKey;

    /**
     * License type of the source data.
     * One of: "PROPRIETARY", "PUBLIC", "OPEN".
     */
    private String licenseType;
}
