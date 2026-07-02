package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.OpportunityMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Repository for accessing computed opportunity metrics.
 * Provides queries for sorting by recency, sector lookups,
 * top-N opportunities, and purging old records.
 */
@Repository
public interface OpportunityMetricsRepository extends JpaRepository<OpportunityMetric, Long> {

    /**
     * Returns all opportunity metrics ordered by computation timestamp descending (most recent first).
     */
    List<OpportunityMetric> findAllByOrderByComputationTimestampDesc();

    /**
     * Returns metrics for a specific sector, ordered by opportunity index descending (best opportunities first).
     *
     * @param sectorName the sector to filter by
     */
    List<OpportunityMetric> findBySectorNameOrderByOpportunityIndexDesc(String sectorName);

    /**
     * Returns the top 5 opportunities across all sectors, ordered by opportunity index descending.
     */
    List<OpportunityMetric> findTop5ByOrderByOpportunityIndexDesc();

    /**
     * Deletes all opportunity metrics whose computation timestamp is before the given cutoff.
     * Intended for data-retention purging.
     *
     * @param cutoff the threshold instant; records older than this are removed
     */
    @Modifying
    @Transactional
    void deleteByComputationTimestampBefore(Instant cutoff);
}
