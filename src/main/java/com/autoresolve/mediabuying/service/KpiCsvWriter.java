package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.model.entity.Platform;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Writes the current {@code kpi_metrics} table to a CSV file on disk.
 * <p>
 * Triggered automatically at the end of each pipeline ingestion cycle
 * (from {@link com.autoresolve.mediabuying.integration.pipeline.KpiBuilder#onBatchComplete}).
 * Writes to {@code kpi-csv-writer.output-path} with a timestamped filename
 * so each cycle produces a separate, browsable file.
 * </p>
 */
@Service
public class KpiCsvWriter {

    private static final Logger log = LoggerFactory.getLogger(KpiCsvWriter.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final KPIMetricsRepository kpiMetricsRepository;
    private final PlatformRepository platformRepository;
    private final CommerceSectorRepository sectorRepository;

    /** Directory where CSV files are written (default: current working directory). */
    private final String outputDirectory;

    /** Whether to auto-dump CSV after each ingestion cycle. */
    private final boolean enabled;

    public KpiCsvWriter(KPIMetricsRepository kpiMetricsRepository,
                        PlatformRepository platformRepository,
                        CommerceSectorRepository sectorRepository,
                        @Value("${kpi-csv-writer.output-directory:.}") String outputDirectory,
                        @Value("${kpi-csv-writer.enabled:true}") boolean enabled) {
        this.kpiMetricsRepository = kpiMetricsRepository;
        this.platformRepository = platformRepository;
        this.sectorRepository = sectorRepository;
        this.outputDirectory = outputDirectory;
        this.enabled = enabled;
        log.info("KpiCsvWriter initialized — enabled={}, output-directory={}", enabled, outputDirectory);
    }

    /**
     * Query all KPI metrics and write them to a timestamped CSV file.
     * Called automatically after each pipeline batch completes.
     */
    public void writeCsv() {
        if (!enabled) {
            log.debug("KpiCsvWriter is disabled — skipping CSV dump");
            return;
        }

        List<KPIMetrics> metricsList = kpiMetricsRepository.findAll();
        if (metricsList.isEmpty()) {
            log.info("No KPI metrics found — skipping CSV dump");
            return;
        }

        String filename = String.format("kpi_export_%s.csv", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        String filepath = outputDirectory.endsWith("/") || outputDirectory.endsWith("\\")
                ? outputDirectory + filename
                : outputDirectory + "/" + filename;

        try (CSVWriter csvWriter = new CSVWriter(new FileWriter(filepath))) {
            // Header row
            String[] header = {
                    "Platform", "Sector", "ROAS", "CAC", "CLTV",
                    "Conversion Rate", "Contribution Margin", "Payback Period",
                    "Incremental Return", "CPQL", "Scalability",
                    "Cash Conversion Cycle", "Saturation Point",
                    "Attribution Accuracy", "Data Timestamp", "Data Source"
            };
            csvWriter.writeNext(header);

            // Data rows
            for (KPIMetrics m : metricsList) {
                String platformName = lookupPlatformName(m.getPlatformId());
                String sectorName = lookupSectorName(m.getSectorId());

                String[] row = {
                        platformName,
                        sectorName,
                        fmt(m.getRoas()),
                        fmt(m.getCac()),
                        fmt(m.getCltv()),
                        pct(m.getConversionRate()),
                        fmt(m.getContributionMargin()),
                        fmt(m.getPaybackPeriod()),
                        pct(m.getIncrementalReturn()),
                        fmt(m.getCostPerQualifiedLead()),
                        fmt(m.getScalability()),
                        fmt(m.getCashConversionCycle()),
                        pct(m.getSaturationPoint()),
                        pct(m.getAttributionAccuracy()),
                        m.getIngestionTimestamp() != null ? m.getIngestionTimestamp().toString() : "",
                        m.getDataSource() != null ? m.getDataSource() : ""
                };
                csvWriter.writeNext(row);
            }

            log.info("Exported {} KPI row(s) to {}", metricsList.size(), filepath);

        } catch (IOException e) {
            log.error("Failed to write KPI CSV to {}: {}", filepath, e.getMessage(), e);
        }
    }

    // ---- internal helpers ----

    private String lookupPlatformName(Long id) {
        if (id == null) return "";
        Optional<Platform> platform = platformRepository.findById(id);
        return platform.map(p -> p.getDisplayName() != null ? p.getDisplayName() : p.getName())
                .orElse("Platform#" + id);
    }

    private String lookupSectorName(Long id) {
        if (id == null) return "";
        Optional<CommerceSector> sector = sectorRepository.findById(id);
        return sector.map(s -> s.getDisplayName() != null ? s.getDisplayName() : s.getName())
                .orElse("Sector#" + id);
    }

    private static String fmt(Object value) {
        return value != null ? value.toString() : "";
    }

    /** Format a decimal-as-percentage (e.g. 0.045 → "4.50%"). */
    private static String pct(Object value) {
        if (value == null) return "";
        try {
            double v = Double.parseDouble(value.toString());
            return String.format("%.2f%%", v * 100);
        } catch (NumberFormatException e) {
            return value.toString();
        }
    }
}
