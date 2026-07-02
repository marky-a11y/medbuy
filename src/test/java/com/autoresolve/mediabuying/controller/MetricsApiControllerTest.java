package com.autoresolve.mediabuying.controller;

import com.autoresolve.mediabuying.controller.api.MetricsApiController;
import com.autoresolve.mediabuying.model.dto.KPIMetricsDTO;
import com.autoresolve.mediabuying.model.dto.PageDTO;
import com.autoresolve.mediabuying.service.KPIQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsApiControllerTest {

    @Mock
    private KPIQueryService kpiQueryService;

    @Test
    void testGetMetricsReturnsPage() {
        MetricsApiController controller = new MetricsApiController(kpiQueryService);

        KPIMetricsDTO dto = KPIMetricsDTO.builder()
                .platformId(1L)
                .platformName("Google Ads")
                .sectorId(1L)
                .sectorName("Technology")
                .roas(BigDecimal.valueOf(4.5))
                .build();

        Page<KPIMetricsDTO> page = new PageImpl<>(Collections.singletonList(dto));
        when(kpiQueryService.getMetrics(anyLong(), anyLong(), anyInt(), anyInt(),
                anyString(), anyString())).thenReturn(page);

        ResponseEntity<PageDTO<KPIMetricsDTO>> response =
                controller.getMetrics(1L, 1L, "roas", "desc", 0, 20);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        PageDTO<KPIMetricsDTO> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.getTotalElements());
        assertEquals("Google Ads", body.getContent().get(0).getPlatformName());
    }
}
