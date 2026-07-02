package com.autoresolve.mediabuying.controller.dashboard;

import com.autoresolve.mediabuying.model.dto.KPIMetricsDTO;
import com.autoresolve.mediabuying.service.KPIQueryService;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * PrimeFaces LazyDataModel for the 13-column KPI metrics table.
 * Prototype-scoped per view for thread safety.
 * Uses PrimeFaces 11 API (SortMeta.getField(), getOrder()).
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class KpiLazyDataModel extends LazyDataModel<KPIMetricsDTO> {

    private static final Logger log = LoggerFactory.getLogger(KpiLazyDataModel.class);
    private static final long serialVersionUID = 1L;

    @Autowired
    private transient KPIQueryService kpiQueryService;

    private Long platformId;
    private Long sectorId;

    public KpiLazyDataModel() {
        // Default constructor for proxy creation
    }

    public KpiLazyDataModel(KPIQueryService kpiQueryService) {
        this.kpiQueryService = kpiQueryService;
    }

    @Override
    public List<KPIMetricsDTO> load(int first, int pageSize,
                                     Map<String, SortMeta> sortBy,
                                     Map<String, FilterMeta> filterBy) {
        if (platformId == null || sectorId == null) {
            log.debug("LazyDataModel.load() skipped: platformId or sectorId is null");
            this.setRowCount(0);
            return java.util.Collections.emptyList();
        }

        // Extract sort field and order from SortMeta map (PrimeFaces 11 API)
        String sortField = "roas";
        SortOrder sortOrder = SortOrder.DESCENDING;
        if (sortBy != null && !sortBy.isEmpty()) {
            SortMeta meta = sortBy.values().iterator().next();
            if (meta.getField() != null) {
                sortField = meta.getField();
            }
            if (meta.getOrder() != null) {
                sortOrder = meta.getOrder();
            }
        }

        int page = pageSize > 0 ? first / pageSize : 0;
        String sortDir = (sortOrder == SortOrder.ASCENDING) ? "asc" : "desc";
        String sortCol = sortField;

        log.debug("LazyDataModel loading: platform={}, sector={}, page={}, size={}, sort={} {}",
                platformId, sectorId, page, pageSize, sortCol, sortDir);

        try {
            org.springframework.data.domain.Page<KPIMetricsDTO> result = kpiQueryService.getMetrics(
                    platformId, sectorId, page, pageSize, sortCol, sortDir);

            this.setRowCount((int) result.getTotalElements());
            log.debug("LazyDataModel loaded {} rows out of {} total",
                    result.getContent().size(), result.getTotalElements());

            return result.getContent();
        } catch (Exception e) {
            log.error("LazyDataModel load failed: platform={}, sector={}",
                    platformId, sectorId, e);
            this.setRowCount(0);
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public int count(Map<String, FilterMeta> filterBy) {
        if (platformId == null || sectorId == null) {
            return 0;
        }
        try {
            // Use a page size of 1 to get the count quickly
            org.springframework.data.domain.Page<KPIMetricsDTO> result = kpiQueryService.getMetrics(
                    platformId, sectorId, 0, 1, "roas", "desc");
            return (int) result.getTotalElements();
        } catch (Exception e) {
            log.error("LazyDataModel count failed: platform={}, sector={}",
                    platformId, sectorId, e);
            return 0;
        }
    }

    // --- Getters and Setters ---

    public Long getPlatformId() { return platformId; }
    public void setPlatformId(Long platformId) { this.platformId = platformId; }
    public Long getSectorId() { return sectorId; }
    public void setSectorId(Long sectorId) { this.sectorId = sectorId; }
}
