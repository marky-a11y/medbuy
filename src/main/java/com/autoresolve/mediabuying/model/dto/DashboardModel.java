package com.autoresolve.mediabuying.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardModel implements Serializable {

    private static final long serialVersionUID = 1L;

    private TopOpportunityDTO topOpportunity;
    private List<PlatformDTO> platforms;
    private Long sectorFilter;
    private Instant lastRefreshed;
}
