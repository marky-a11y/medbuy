package com.autoresolve.mediabuying.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopOpportunityDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long platformId;
    private String platformName;
    private Long sectorId;
    private String sectorName;
    private Double compositeScore;
    private String qualitativeBadge;
    private Map<String, Double> primaryKpis;
    private Map<String, Double> allKpis;
    private Instant computedAt;
    private List<ClientProspectDTO> topClients;

    /**
     * Returns a placeholder DTO when no data is available.
     */
    public static TopOpportunityDTO placeholder() {
        return TopOpportunityDTO.builder()
                .platformId(0L)
                .platformName("No data available")
                .sectorId(0L)
                .sectorName("")
                .compositeScore(0.0)
                .qualitativeBadge("N/A")
                .topClients(Collections.emptyList())
                .computedAt(Instant.now())
                .build();
    }
}
