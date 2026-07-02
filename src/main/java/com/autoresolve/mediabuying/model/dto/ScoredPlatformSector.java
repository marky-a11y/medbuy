package com.autoresolve.mediabuying.model.dto;

import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ScoredPlatformSector {
    long platformId;
    String platformName;
    long sectorId;
    String sectorName;
    double score;
    KPIMetrics rawMetrics;
}
