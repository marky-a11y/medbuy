package com.autoresolve.mediabuying.controller;

import com.autoresolve.mediabuying.integration.wrapper.DataSourceStatusProvider;
import com.autoresolve.mediabuying.model.dto.KpiDebugDTO;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.model.entity.Company;
import com.autoresolve.mediabuying.model.entity.IngestionLog;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.model.entity.OpportunityMetric;
import com.autoresolve.mediabuying.model.entity.Platform;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.repository.CompanyRepository;
import com.autoresolve.mediabuying.repository.IngestionLogRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.OpportunityMetricsRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Serves the Pipeline Debug page, showing raw ingestion logs,
 * aggregated KPI metrics, and extracted companies side by side
 * for diagnosing the data pipeline.
 */
@Controller
public class PipelineDebugController {

    private static final Logger log = LoggerFactory.getLogger(PipelineDebugController.class);

    private final IngestionLogRepository ingestionLogRepository;
    private final KPIMetricsRepository kpiMetricsRepository;
    private final PlatformRepository platformRepository;
    private final CommerceSectorRepository sectorRepository;
    private final CompanyRepository companyRepository;
    private final OpportunityMetricsRepository opportunityMetricsRepository;
    private final List<DataSourceStatusProvider> wrappers;

    public PipelineDebugController(IngestionLogRepository ingestionLogRepository,
                                   KPIMetricsRepository kpiMetricsRepository,
                                   PlatformRepository platformRepository,
                                   CommerceSectorRepository sectorRepository,
                                   CompanyRepository companyRepository,
                                   OpportunityMetricsRepository opportunityMetricsRepository,
                                   List<DataSourceStatusProvider> wrappers) {
        this.ingestionLogRepository = ingestionLogRepository;
        this.kpiMetricsRepository = kpiMetricsRepository;
        this.platformRepository = platformRepository;
        this.sectorRepository = sectorRepository;
        this.companyRepository = companyRepository;
        this.opportunityMetricsRepository = opportunityMetricsRepository;
        this.wrappers = wrappers;
    }

    @GetMapping("/pipeline-debug")
    public String pipelineDebug(Model model) {
        // 1) Raw ingestion logs — most recent 50
        List<IngestionLog> recentLogs = ingestionLogRepository.findTop20ByOrderByIngestionTimestampDesc();
        model.addAttribute("recentLogs", recentLogs != null ? recentLogs : Collections.emptyList());

        // 2) KPI metrics with resolved platform/sector names
        List<KpiDebugDTO> kpiMetrics = buildKpiMetrics();
        model.addAttribute("kpiMetrics", kpiMetrics);

        // 3) Companies with placeholder sector name (resolved from sectorId)
        List<Company> companies = companyRepository.findAll();
        model.addAttribute("companies", companies != null ? companies : Collections.emptyList());

        // 4) Sector name lookup for the template to display company sector names
        Map<Long, String> sectorNames = resolveSectorNames();
        model.addAttribute("sectorNames", sectorNames);

        // 5) Wrapper live/mock status for the status grid
        model.addAttribute("wrapperStatuses", wrappers);

        // 6) Opportunity metrics
        List<OpportunityMetric> opportunityMetrics = opportunityMetricsRepository
                .findTop5ByOrderByOpportunityIndexDesc();
        model.addAttribute("opportunityMetrics", opportunityMetrics);

        log.debug("Pipeline debug page: {} logs, {} KPIs, {} companies, {} opportunity metrics",
                recentLogs != null ? recentLogs.size() : 0,
                kpiMetrics.size(),
                companies != null ? companies.size() : 0);

        return "pipeline-debug";
    }

    /**
     * Build KPI debug DTOs with resolved platform and sector display names.
     */
    private List<KpiDebugDTO> buildKpiMetrics() {
        List<KPIMetrics> allMetrics = kpiMetricsRepository.findAll();
        if (allMetrics == null || allMetrics.isEmpty()) {
            return Collections.emptyList();
        }

        List<KpiDebugDTO> result = new ArrayList<>(allMetrics.size());
        for (KPIMetrics m : allMetrics) {
            String platformName = lookupPlatformName(m.getPlatformId());
            String sectorName = lookupSectorName(m.getSectorId());

            result.add(new KpiDebugDTO(
                    platformName,
                    sectorName,
                    m.getRoas(),
                    m.getCac(),
                    m.getCltv(),
                    m.getConversionRate(),
                    m.getScalability(),
                    m.getAttributionAccuracy(),
                    m.getIngestionTimestamp()
            ));
        }
        return result;
    }

    /**
     * Build a lookup map from sector ID to display name for the template.
     */
    private Map<Long, String> resolveSectorNames() {
        List<CommerceSector> sectors = sectorRepository.findAll();
        if (sectors == null || sectors.isEmpty()) {
            return Collections.emptyMap();
        }
        return sectors.stream()
                .collect(Collectors.toMap(
                        CommerceSector::getId,
                        s -> s.getDisplayName() != null ? s.getDisplayName() : s.getName()
                ));
    }

    private String lookupPlatformName(Long id) {
        if (id == null) return "Platform#" + id;
        Optional<Platform> platform = platformRepository.findById(id);
        return platform.map(p -> p.getDisplayName() != null ? p.getDisplayName() : p.getName())
                .orElse("Platform#" + id);
    }

    private String lookupSectorName(Long id) {
        if (id == null) return "Sector#" + id;
        Optional<CommerceSector> sector = sectorRepository.findById(id);
        return sector.map(s -> s.getDisplayName() != null ? s.getDisplayName() : s.getName())
                .orElse("Sector#" + id);
    }
}
