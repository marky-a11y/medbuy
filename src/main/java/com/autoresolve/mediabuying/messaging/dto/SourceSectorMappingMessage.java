package com.autoresolve.mediabuying.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceSectorMappingMessage {

    private String eventId;
    private String sourceName;
    private List<String> matchedSectors;
    private String classificationMethod;
    private Double confidenceScore;
    private String rawEventId;
    private Instant processingTimestamp;
    private String partitionKey;
}
