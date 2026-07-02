package com.autoresolve.mediabuying.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RawKPIEvent {

    private String eventId;
    private String platformName;
    private String sectorName;
    private String dataSource;
    private List<String> sourceReferences;
    private Instant ingestionTimestamp;

    // Core KPIs (6 inputs to composite scoring)
    private Double roas;
    private Double cac;
    private Double cltv;
    private Double conversionRate;
    private Double scalability;
    private Double attributionAccuracy;

    // Extended KPIs
    private Double contributionMargin;
    private Double paybackPeriod;
    private Double incrementalReturn;
    private Double cpql;
    private Double cashConversionCycle;
    private Double saturationPoint;
}
