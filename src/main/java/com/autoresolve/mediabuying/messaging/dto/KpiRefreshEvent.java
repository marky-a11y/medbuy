package com.autoresolve.mediabuying.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiRefreshEvent {

    private Long platformId;
    private Long sectorId;
    private String eventType;
    private Instant refreshTimestamp;
}
