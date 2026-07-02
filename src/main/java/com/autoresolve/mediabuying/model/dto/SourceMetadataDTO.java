package com.autoresolve.mediabuying.model.dto;

import com.autoresolve.mediabuying.model.entity.DataSource;
import com.autoresolve.mediabuying.model.entity.KpiSourceAttribution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceMetadataDTO {

    private String sourceName;
    private String sourceType;
    private String sourceUrl;
    private String licenseType;
    private Instant lastVerifiedAt;
    private boolean isStale;
    private String attributionContext;
    private String platformName;

    /**
     * Creates a SourceMetadataDTO from a KpiSourceAttribution and DataSource pair.
     * The {@code isStale} flag is true when lastVerifiedAt is null or older than 30 days.
     */
    public static SourceMetadataDTO from(KpiSourceAttribution ksa, DataSource ds) {
        boolean stale = ds.getLastVerifiedAt() == null
                || ds.getLastVerifiedAt().isBefore(Instant.now().minus(30, ChronoUnit.DAYS));

        return SourceMetadataDTO.builder()
                .sourceName(ds.getSourceName())
                .sourceType(ds.getSourceType())
                .sourceUrl(ds.getSourceUrl())
                .licenseType(ds.getLicenseType())
                .lastVerifiedAt(ds.getLastVerifiedAt())
                .isStale(stale)
                .attributionContext(ksa.getAttributionContext())
                .platformName(ds.getPlatform() != null ? ds.getPlatform().getDisplayName() : null)
                .build();
    }
}
