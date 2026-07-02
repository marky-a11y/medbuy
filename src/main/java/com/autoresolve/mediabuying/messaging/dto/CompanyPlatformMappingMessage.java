package com.autoresolve.mediabuying.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyPlatformMappingMessage {

    private String eventId;
    private String companyName;
    private String sectorName;
    private List<String> inferredAdPlatforms;
    private String mappingMethod;
    private Double confidenceScore;
    private String sourceSectorEventId;
    private Instant processingTimestamp;
    private String partitionKey;
    /** Name of the original data-source wrapper that produced this mapping, e.g. "yelp_fusion". */
    private String sourceName;
}
