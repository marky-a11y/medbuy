package com.autoresolve.mediabuying.model.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight DTO for displaying KPI metrics on the Pipeline Debug page.
 * Resolves platform and sector names from their IDs for human-readable output.
 */
public class KpiDebugDTO {

    private final String platformName;
    private final String sectorName;
    private final BigDecimal roas;
    private final BigDecimal cac;
    private final BigDecimal cltv;
    private final BigDecimal conversionRate;
    private final BigDecimal scalability;
    private final BigDecimal attributionAccuracy;
    private final Instant ingestionTimestamp;

    public KpiDebugDTO(String platformName, String sectorName,
                       BigDecimal roas, BigDecimal cac, BigDecimal cltv,
                       BigDecimal conversionRate, BigDecimal scalability,
                       BigDecimal attributionAccuracy, Instant ingestionTimestamp) {
        this.platformName = platformName;
        this.sectorName = sectorName;
        this.roas = roas;
        this.cac = cac;
        this.cltv = cltv;
        this.conversionRate = conversionRate;
        this.scalability = scalability;
        this.attributionAccuracy = attributionAccuracy;
        this.ingestionTimestamp = ingestionTimestamp;
    }

    public String getPlatformName() { return platformName; }
    public String getSectorName() { return sectorName; }
    public BigDecimal getRoas() { return roas; }
    public BigDecimal getCac() { return cac; }
    public BigDecimal getCltv() { return cltv; }
    public BigDecimal getConversionRate() { return conversionRate; }
    public BigDecimal getScalability() { return scalability; }
    public BigDecimal getAttributionAccuracy() { return attributionAccuracy; }
    public Instant getIngestionTimestamp() { return ingestionTimestamp; }
}
