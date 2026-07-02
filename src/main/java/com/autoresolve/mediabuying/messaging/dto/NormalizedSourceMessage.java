package com.autoresolve.mediabuying.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedSourceMessage {

    private String eventId;
    private String sourceName;
    private String sourceUrl;
    private String sourceType;
    private String rawData;
    private String normalizedSummary;
    private Instant ingestionTimestamp;
    private String ingestionKey;
}
