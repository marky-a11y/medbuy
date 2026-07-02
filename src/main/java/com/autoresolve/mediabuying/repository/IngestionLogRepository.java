package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.IngestionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for accessing ingestion cycle log entries.
 */
@Repository
public interface IngestionLogRepository extends JpaRepository<IngestionLog, Long> {

    /**
     * Returns all ingestion logs ordered by ingestion timestamp descending (most recent first).
     */
    List<IngestionLog> findAllByOrderByIngestionTimestampDesc();

    /**
     * Returns all ingestion logs for a given cycle, ordered by ingestion timestamp ascending.
     *
     * @param cycleId the UUID of the ingestion cycle
     */
    List<IngestionLog> findByCycleIdOrderByIngestionTimestampAsc(UUID cycleId);

    /**
     * Returns the 20 most recent ingestion logs.
     */
    List<IngestionLog> findTop20ByOrderByIngestionTimestampDesc();
}
