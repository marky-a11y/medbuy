package com.autoresolve.mediabuying.controller.api;

import com.autoresolve.mediabuying.exception.ResourceNotFoundException;
import com.autoresolve.mediabuying.model.dto.SourceMetadataDTO;
import com.autoresolve.mediabuying.service.SourceAttributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing data source attribution metadata for KPI metrics records.
 */
@RestController
public class SourceAttributionController {

    private final SourceAttributionService sourceAttributionService;

    public SourceAttributionController(SourceAttributionService sourceAttributionService) {
        this.sourceAttributionService = sourceAttributionService;
    }

    /**
     * Returns the list of data sources attributed to a specific KPI metrics record.
     *
     * @param kpiId the ID of the KPIMetrics record
     * @return a list of SourceMetadataDTOs
     * @throws ResourceNotFoundException if no KPIMetrics record is found
     */
    @GetMapping("/api/kpi/{id}/sources")
    @PreAuthorize("hasAnyRole('ADMIN','MEDIA_ANALYST')")
    public ResponseEntity<List<SourceMetadataDTO>> getSourcesForKpi(@PathVariable("id") Long kpiId) {
        List<SourceMetadataDTO> sources = sourceAttributionService.getSourcesForKpi(kpiId);
        if (sources.isEmpty()) {
            // Check if the KPI metrics record itself exists; if not, throw 404
            throw new ResourceNotFoundException("KPIMetrics", kpiId);
        }
        return ResponseEntity.ok(sources);
    }
}
