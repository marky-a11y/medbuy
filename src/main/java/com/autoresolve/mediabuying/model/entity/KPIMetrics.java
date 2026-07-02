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
import javax.persistence.Index;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "kpi_metrics", schema = "media_buying",
       indexes = {
           @Index(name = "idx_kpi_platform_sector", columnList = "platform_id, sector_id"),
           @Index(name = "idx_kpi_ingestion", columnList = "ingestion_timestamp DESC")
       })
public class KPIMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    // Composite Score Inputs (6 KPIs)
    @Column(name = "roas", precision = 12, scale = 2)
    private BigDecimal roas;

    @Column(name = "cac", precision = 12, scale = 2)
    private BigDecimal cac;

    @Column(name = "cltv", precision = 12, scale = 2)
    private BigDecimal cltv;

    @Column(name = "conversion_rate", precision = 7, scale = 4)
    private BigDecimal conversionRate;

    @Column(name = "scalability", precision = 14, scale = 2)
    private BigDecimal scalability;

    @Column(name = "attribution_accuracy", precision = 7, scale = 4)
    private BigDecimal attributionAccuracy;

    // Extended KPI Columns
    @Column(name = "contribution_margin", precision = 12, scale = 2)
    private BigDecimal contributionMargin;

    @Column(name = "payback_period", precision = 8, scale = 2)
    private BigDecimal paybackPeriod;

    @Column(name = "incremental_return", precision = 12, scale = 2)
    private BigDecimal incrementalReturn;

    @Column(name = "cost_per_qualified_lead", precision = 12, scale = 2)
    private BigDecimal costPerQualifiedLead;

    @Column(name = "cash_conversion_cycle", precision = 8, scale = 2)
    private BigDecimal cashConversionCycle;

    @Column(name = "saturation_point", precision = 7, scale = 4)
    private BigDecimal saturationPoint;

    // Metadata
    @Column(name = "ingestion_timestamp")
    private Instant ingestionTimestamp;

    @Column(name = "data_source", length = 50)
    private String dataSource;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
