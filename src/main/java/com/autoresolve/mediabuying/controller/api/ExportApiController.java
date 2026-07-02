package com.autoresolve.mediabuying.controller.api;

import com.autoresolve.mediabuying.service.CsvExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/export")
public class ExportApiController {

    private final CsvExportService csvExportService;

    public ExportApiController(CsvExportService csvExportService) {
        this.csvExportService = csvExportService;
    }

    @GetMapping("/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDIA_ANALYST')")
    public ResponseEntity<String> exportCsv(
            @RequestParam(value = "platform", required = false) Long platformId,
            @RequestParam(value = "sector", required = false) Long sectorId) {

        String csvContent = csvExportService.generateCsv(platformId, sectorId);

        String filename = "kpi_metrics_export.csv";
        if (platformId != null && sectorId != null) {
            filename = "kpi_metrics_" + platformId + "_" + sectorId + ".csv";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvContent);
    }
}
