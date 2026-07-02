package com.autoresolve.mediabuying.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload published to the {@code pipeline.batch-complete} topic at the end
 * of each source-ingestion cycle.
 * <p>
 * Carries aggregate statistics about the batch so downstream listeners
 * (monitoring, alerting, dashboards) can react without inspecting every
 * individual {@code source.raw} event.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineBatchCompleteEvent {

    /** Unique identifier for this ingestion batch. */
    private UUID batchId;

    /** How many source wrappers were invoked in this cycle (always 10). */
    private int totalSources;

    /** How many wrappers returned usable data (non-null {@code RawSourceData}). */
    private int successfulSources;

    /** Names of sources that threw an exception or returned null. */
    private List<String> failedSources;

    /** When the batch cycle completed. */
    private Instant cycleTimestamp;
}
