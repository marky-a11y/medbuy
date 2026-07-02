package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.dto.KPIMetricsDTO;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.model.entity.Platform;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CsvExportService {

    private static final Logger log = LoggerFactory.getLogger(CsvExportService.class);

    private final KPIMetricsRepository kpiMetricsRepository;
    private final PlatformRepository platformRepository;
    private final CommerceSectorRepository sectorRepository;
    private final SourceAttributionService sourceAttributionService;

    public CsvExportService(KPIMetricsRepository kpiMetricsRepository,
                             PlatformRepository platformRepository,
                             CommerceSectorRepository sectorRepository,
                             SourceAttributionService sourceAttributionService) {
        this.kpiMetricsRepository = kpiMetricsRepository;
        this.platformRepository = platformRepository;
        this.sectorRepository = sectorRepository;
        this.sourceAttributionService = sourceAttributionService;
    }

    public String generateCsv(Long platformId, Long sectorId) {
        List<KPIMetrics> metricsList;

        if (platformId != null && sectorId != null) {
            metricsList = kpiMetricsRepository
                    .findByPlatformIdAndSectorId(platformId, sectorId,
                            org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE))
                    .getContent();
        } else {
            metricsList = kpiMetricsRepository.findAll();
        }

        StringWriter stringWriter = new StringWriter();

        // Batch-resolve source names for all metrics in the export
        Set<Long> allKpiIds = metricsList.stream().map(KPIMetrics::getId).collect(Collectors.toSet());
        Map<Long, String> sourceNamesMap = allKpiIds.isEmpty()
                ? Collections.emptyMap()
                : sourceAttributionService.getPrimarySourceNames(allKpiIds);

        try (CSVWriter csvWriter = new CSVWriter(stringWriter)) {
            // Write header
            String[] header = {
                    "Platform", "Sector", "ROAS", "CAC", "CLTV", "Conversion Rate",
                    "Contribution Margin", "Payback Period", "Incremental Return",
                    "CPQL", "Scalability", "Cash Conversion Cycle", "Saturation Point",
                    "Attribution Accuracy", "Data Timestamp", "Source"
            };
            csvWriter.writeNext(header);

            // Write data rows
            for (KPIMetrics metrics : metricsList) {
                String platformName = platformRepository.findById(metrics.getPlatformId())
                        .map(Platform::getDisplayName).orElse("Unknown");
                String sectorName = sectorRepository.findById(metrics.getSectorId())
                        .map(CommerceSector::getDisplayName).orElse("Unknown");

                String sourceName = sourceNamesMap.getOrDefault(metrics.getId(), "");
                String[] row = {
                        platformName,
                        sectorName,
                        formatDecimal(metrics.getRoas()),
                        formatDecimal(metrics.getCac()),
                        formatDecimal(metrics.getCltv()),
                        formatPercentage(metrics.getConversionRate()),
                        formatDecimal(metrics.getContributionMargin()),
                        formatDecimal(metrics.getPaybackPeriod()),
                        formatPercentage(metrics.getIncrementalReturn()),
                        formatDecimal(metrics.getCostPerQualifiedLead()),
                        formatDecimal(metrics.getScalability()),
                        formatDecimal(metrics.getCashConversionCycle()),
                        formatPercentage(metrics.getSaturationPoint()),
                        formatPercentage(metrics.getAttributionAccuracy()),
                        metrics.getIngestionTimestamp() != null ?
                                metrics.getIngestionTimestamp().toString() : "",
                        sourceName
                };
                csvWriter.writeNext(row);
            }
        } catch (Exception e) {
            log.error("Failed to generate CSV", e);
            throw new RuntimeException("Failed to generate CSV", e);
        }

        return stringWriter.toString();
    }

    private String formatDecimal(Object value) {
        return value != null ? value.toString() : "";
    }

    private String formatPercentage(Object value) {
        if (value == null) return "";
        try {
            double v = Double.parseDouble(value.toString());
            return String.format("%.2f%%", v * 100);
        } catch (NumberFormatException e) {
            return value.toString();
        }
    }
}
