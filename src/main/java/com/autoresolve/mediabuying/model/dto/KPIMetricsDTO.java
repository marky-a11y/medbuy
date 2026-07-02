package com.autoresolve.mediabuying.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KPIMetricsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long platformId;
    private String platformName;
    private Long sectorId;
    private String sectorName;

    // Core KPIs
    private BigDecimal roas;
    private BigDecimal cac;
    private BigDecimal cltv;
    private BigDecimal conversionRate;
    private BigDecimal scalability;
    private BigDecimal attributionAccuracy;

    // Extended KPIs
    private BigDecimal contributionMargin;
    private BigDecimal paybackPeriod;
    private BigDecimal incrementalReturn;
    private BigDecimal costPerQualifiedLead;
    private BigDecimal cashConversionCycle;
    private BigDecimal saturationPoint;

    // Metadata
    private Instant ingestionTimestamp;
    private String dataSource;
    private Boolean dataStale;

    // Source Attribution
    private String primarySourceName;
}
