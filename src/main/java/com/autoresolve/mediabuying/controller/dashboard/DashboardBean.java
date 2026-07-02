package com.autoresolve.mediabuying.controller.dashboard;

import com.autoresolve.mediabuying.model.dto.DashboardModel;
import com.autoresolve.mediabuying.model.dto.KPIMetricsDTO;
import com.autoresolve.mediabuying.model.dto.PlatformDTO;
import com.autoresolve.mediabuying.model.dto.SectorDTO;
import com.autoresolve.mediabuying.model.dto.SourceMetadataDTO;
import com.autoresolve.mediabuying.model.dto.TopOpportunityDTO;
import com.autoresolve.mediabuying.service.DashboardService;
import com.autoresolve.mediabuying.service.PlatformSectorService;
import com.autoresolve.mediabuying.service.SourceAttributionService;
import org.primefaces.model.LazyDataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * View-scoped managed bean for the JSF dashboard page.
 * Uses Spring @Component with JSF view scope (registered by Joinfaces).
 */
@Component
@Scope("view")
public class DashboardBean implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(DashboardBean.class);
    private static final long serialVersionUID = 1L;

    @Autowired
    private transient DashboardService dashboardService;

    @Autowired
    private transient PlatformSectorService platformSectorService;

    @Autowired
    private transient KpiLazyDataModel kpiLazyDataModel;

    @Autowired
    private transient SourceAttributionService sourceAttributionService;

    private DashboardModel dashboardModel;
    private List<PlatformDTO> platforms;
    private List<SectorDTO> expandedSectors;
    private TopOpportunityDTO topOpportunity;
    private Long selectedPlatformId;
    private Long selectedSectorId;
    private boolean metricsTableVisible;
    private Long sectorFilterId;
    private Instant lastRefreshTime;
    private Double dailyAdSpend;

    // Source citation cache
    private final Map<Long, SourceCacheEntry> sourceCache = new HashMap<>();
    private List<SourceMetadataDTO> currentSources;
    private Long currentKpiId;

    public DashboardBean() {
        this.lastRefreshTime = Instant.now();
        this.dailyAdSpend = 42800.0;
        this.metricsTableVisible = false;
    }

    @PostConstruct
    public void init() {
        // Read sector filter from request parameter
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (facesContext != null) {
            ExternalContext externalContext = facesContext.getExternalContext();
            String sectorParam = externalContext.getRequestParameterMap().get("sector");
            if (sectorParam != null && !sectorParam.isEmpty()) {
                try {
                    sectorFilterId = Long.parseLong(sectorParam);
                } catch (NumberFormatException e) {
                    log.warn("Invalid sector parameter: {}", sectorParam);
                    sectorFilterId = null;
                }
            }

            // Check for platform pre-select parameter (from "Implement Now" button)
            String platformParam = externalContext.getRequestParameterMap().get("platform");
            String sectorParam2 = externalContext.getRequestParameterMap().get("sector");
            if (platformParam != null && sectorParam2 != null && sectorFilterId == null) {
                try {
                    Long preselectPlatformId = Long.parseLong(platformParam);
                    Long preselectSectorId = Long.parseLong(sectorParam2);
                    // Auto-expand and select
                    expandPlatform(preselectPlatformId);
                    selectSector(preselectPlatformId, preselectSectorId);
                } catch (NumberFormatException e) {
                    log.warn("Invalid platform/sector preselect params");
                }
            }
        }

        // Load dashboard data
        try {
            if (dashboardService != null) {
                dashboardModel = dashboardService.getDashboard(sectorFilterId);
                platforms = dashboardModel.getPlatforms();
                topOpportunity = dashboardModel.getTopOpportunity();
                lastRefreshTime = dashboardModel.getLastRefreshed();
            } else {
                log.warn("DashboardService not available");
                platforms = Collections.emptyList();
                topOpportunity = TopOpportunityDTO.placeholder();
            }
        } catch (Exception e) {
            log.error("Failed to load dashboard", e);
            platforms = Collections.emptyList();
            topOpportunity = TopOpportunityDTO.placeholder();
        }

        log.info("Dashboard initialized: {} platforms, sectorFilter={}",
                platforms != null ? platforms.size() : 0, sectorFilterId);
    }

    /**
     * Expands a platform to load its sectors (Level 2).
     */
    public void expandPlatform(Long platformId) {
        log.debug("Expanding platform: {}", platformId);
        try {
            if (platformSectorService != null) {
                expandedSectors = platformSectorService.getSectorsForPlatform(platformId);
            } else {
                expandedSectors = Collections.emptyList();
            }
            selectedPlatformId = platformId;
            // Hide metrics table when switching platforms
            metricsTableVisible = false;
            selectedSectorId = null;
        } catch (Exception e) {
            log.error("Failed to expand platform: {}", platformId, e);
            expandedSectors = Collections.emptyList();
        }
    }

    /**
     * Selects a sector and loads the metrics table (Level 3).
     */
    public void selectSector(Long platformId, Long sectorId) {
        log.debug("Selecting sector: platform={}, sector={}", platformId, sectorId);
        selectedPlatformId = platformId;
        selectedSectorId = sectorId;
        metricsTableVisible = true;

        // Configure the LazyDataModel
        if (kpiLazyDataModel != null) {
            kpiLazyDataModel.setPlatformId(platformId);
            kpiLazyDataModel.setSectorId(sectorId);
        }
    }

    /**
     * Navigate to top opportunity: redirects to dashboard with platform/sector params.
     */
    public String navigateToOpportunity() {
        if (topOpportunity != null && topOpportunity.getPlatformId() != null
                && topOpportunity.getPlatformId() > 0) {
            return "/dashboard.xhtml?platform=" + topOpportunity.getPlatformId()
                    + "&sector=" + topOpportunity.getSectorId()
                    + "&faces-redirect=true";
        }
        return null;
    }

    /**
     * Manual refresh of dashboard data.
     */
    public void manualRefresh() {
        log.info("Manual refresh triggered");
        invalidateSourceCache();
        init();
        lastRefreshTime = Instant.now();
    }

    public long getMinutesSinceRefresh() {
        if (lastRefreshTime == null) return 0;
        return ChronoUnit.MINUTES.between(lastRefreshTime, Instant.now());
    }

    // --- Getters ---

    public List<PlatformDTO> getPlatforms() { return platforms; }
    public TopOpportunityDTO getTopOpportunity() { return topOpportunity; }
    public List<SectorDTO> getExpandedSectors() { return expandedSectors; }
    public boolean isMetricsTableVisible() { return metricsTableVisible; }
    public Long getSelectedPlatformId() { return selectedPlatformId; }
    public Long getSelectedSectorId() { return selectedSectorId; }
    public Long getSectorFilterId() { return sectorFilterId; }
    public boolean isSectorFiltered() { return sectorFilterId != null; }

    public double getDailyAdSpend() {
        return dailyAdSpend != null ? dailyAdSpend : 0.0;
    }

    public void setDailyAdSpend(Double dailyAdSpend) {
        this.dailyAdSpend = dailyAdSpend;
    }

    public LazyDataModel<KPIMetricsDTO> getMetricsModel() {
        return kpiLazyDataModel;
    }

    public Instant getLastRefreshTime() {
        return lastRefreshTime;
    }

    // ================================================================
    //  Source Citation Support
    // ================================================================

    /**
     * Holds a cached list of source metadata for a KPI along with a staleness flag.
     */
    public static class SourceCacheEntry {
        private final List<SourceMetadataDTO> sources;
        private final Instant cachedAt;
        private final boolean anyStale;

        public SourceCacheEntry(List<SourceMetadataDTO> sources) {
            this.sources = sources;
            this.cachedAt = Instant.now();
            this.anyStale = sources != null && sources.stream().anyMatch(SourceMetadataDTO::isStale);
        }

        public List<SourceMetadataDTO> getSources() { return sources; }
        public Instant getCachedAt() { return cachedAt; }
        public boolean isAnyStale() { return anyStale; }
    }

    /**
     * Loads source metadata for a given KPI and stores it in the view-level cache.
     * Called from the XHTML commandLink.
     */
    public void loadKpiSources(Long kpiId) {
        log.debug("Loading sources for KPI: {}", kpiId);
        this.currentKpiId = kpiId;

        SourceCacheEntry cached = sourceCache.get(kpiId);
        if (cached != null) {
            log.debug("Source cache hit for KPI: {}", kpiId);
            this.currentSources = cached.getSources();
            return;
        }

        try {
            List<SourceMetadataDTO> sources;
            if (sourceAttributionService != null) {
                sources = sourceAttributionService.getSourcesForKpi(kpiId);
            } else {
                sources = Collections.emptyList();
            }
            SourceCacheEntry entry = new SourceCacheEntry(sources);
            sourceCache.put(kpiId, entry);
            this.currentSources = sources;
        } catch (Exception e) {
            log.error("Failed to load sources for KPI: {}", kpiId, e);
            this.currentSources = Collections.emptyList();
        }
    }

    /**
     * Checks whether any source for the given KPI is stale.
     * Returns false if the KPI is not cached.
     */
    public boolean isKpiSourceStale(Long kpiId) {
        SourceCacheEntry entry = sourceCache.get(kpiId);
        return entry != null && entry.isAnyStale();
    }

    /**
     * Invalidates the entire source cache, forcing a fresh reload on next request.
     * Called on manual refresh.
     */
    public void invalidateSourceCache() {
        sourceCache.clear();
        this.currentSources = null;
        this.currentKpiId = null;
        log.debug("Source cache invalidated");
    }

    public List<SourceMetadataDTO> getCurrentSources() {
        return currentSources;
    }

    public void fabAction() {
        log.debug("FAB clicked (stub action for MVP)");
    }

    /**
     * Maps qualitative badge name to CSS class.
     */
    public String getBadgeCssClass() {
        if (topOpportunity == null || topOpportunity.getQualitativeBadge() == null) {
            return "kpi-badge-medium";
        }
        String badge = topOpportunity.getQualitativeBadge().toLowerCase();
        switch (badge) {
            case "high": return "kpi-badge-high";
            case "low":  return "kpi-badge-low";
            default:     return "kpi-badge-medium";
        }
    }
}
