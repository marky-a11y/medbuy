package com.autoresolve.mediabuying.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized DTO for platform API responses.
 * All platform wrappers return this standardized format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformApiResponse {

    private String platformName;
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
}
