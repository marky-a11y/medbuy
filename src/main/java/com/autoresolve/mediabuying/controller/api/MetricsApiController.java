package com.autoresolve.mediabuying.controller.api;

import com.autoresolve.mediabuying.model.dto.KPIMetricsDTO;
import com.autoresolve.mediabuying.model.dto.PageDTO;
import com.autoresolve.mediabuying.service.KPIQueryService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsApiController {

    private final KPIQueryService kpiQueryService;

    public MetricsApiController(KPIQueryService kpiQueryService) {
        this.kpiQueryService = kpiQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDIA_ANALYST', 'VIEWER')")
    public ResponseEntity<PageDTO<KPIMetricsDTO>> getMetrics(
            @RequestParam("platform") Long platformId,
            @RequestParam("sector") Long sectorId,
            @RequestParam(value = "sort", defaultValue = "roas") String sort,
            @RequestParam(value = "dir", defaultValue = "desc") String dir,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        Page<KPIMetricsDTO> result = kpiQueryService.getMetrics(
                platformId, sectorId, page, size, sort, dir);

        Map<String, String> sortInfo = new HashMap<>();
        sortInfo.put("column", sort);
        sortInfo.put("direction", dir);

        PageDTO<KPIMetricsDTO> pageDTO = PageDTO.<KPIMetricsDTO>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .sort(sortInfo)
                .build();

        return ResponseEntity.ok(pageDTO);
    }
}
