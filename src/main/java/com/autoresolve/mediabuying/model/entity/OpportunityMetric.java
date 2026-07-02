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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Captures opportunity metrics computed for a given sector (and optionally platform).
 * Each row represents a point-in-time assessment of market opportunity based on
 * demand, competition, saturation, CPC, and search-growth signals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "opportunity_metrics", schema = "media_buying")
public class OpportunityMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sector_name", length = 50, nullable = false)
    private String sectorName;

    @Column(name = "platform_name", length = 50)
    private String platformName;

    @Column(name = "market_demand_score", precision = 5, scale = 2)
    private BigDecimal marketDemandScore;

    @Column(name = "advertising_competition_score", precision = 5, scale = 2)
    private BigDecimal advertisingCompetitionScore;

    @Column(name = "local_market_saturation", precision = 5, scale = 2)
    private BigDecimal localMarketSaturation;

    @Column(name = "average_estimated_cpc", precision = 10, scale = 2)
    private BigDecimal averageEstimatedCpc;

    @Column(name = "search_growth", precision = 5, scale = 2)
    private BigDecimal searchGrowth;

    @Column(name = "opportunity_index", precision = 5, scale = 2)
    private BigDecimal opportunityIndex;

    @Column(name = "computation_timestamp", nullable = false)
    private Instant computationTimestamp;

    @Column(name = "data_sources_used", length = 500)
    private String dataSourcesUsed;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
