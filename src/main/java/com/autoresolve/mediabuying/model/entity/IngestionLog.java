package com.autoresolve.mediabuying.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks each ingestion cycle run, including the source fetched, the outcome,
 * and any errors encountered. Used for monitoring and debugging the data
 * ingestion pipeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ingestion_log", schema = "media_buying")
public class IngestionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "source_name", length = 50, nullable = false)
    private String sourceName;

    @Column(name = "fetch_status", length = 20, nullable = false)
    private String fetchStatus;

    @Column(name = "source_type", length = 10)
    private String sourceType;

    @Column(name = "record_count")
    private int recordCount;

    @Column(name = "normalized_summary", length = 500)
    private String normalizedSummary;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "ingestion_timestamp", nullable = false)
    private Instant ingestionTimestamp;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
