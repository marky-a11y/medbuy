package com.autoresolve.mediabuying.controller.dashboard;

import com.autoresolve.mediabuying.model.dto.InsightDTO;
import com.autoresolve.mediabuying.service.InsightsEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightsBeanTest {

    @Mock
    private InsightsEngine insightsEngine;

    @InjectMocks
    private InsightsBean insightsBean;

    @BeforeEach
    void setUp() {
        // The @PostConstruct init() will be called by the container, but in unit tests
        // we need to call it manually since Spring isn't bootstrapping the bean.
    }

    @Test
    void testInitLoadsInsights() {
        // Arrange
        List<InsightDTO> expectedInsights = Collections.singletonList(
                InsightDTO.builder().sectorName("Tech").insightType("GAP").build());
        when(insightsEngine.getClientGapInsights()).thenReturn(expectedInsights);

        // Act — simulate @PostConstruct
        insightsBean.init();

        // Assert
        assertTrue(insightsBean.hasInsights());
        assertEquals(1, insightsBean.getInsights().size());
        assertEquals("Tech", insightsBean.getInsights().get(0).getSectorName());
        assertNull(insightsBean.getErrorMessage());
    }

    @Test
    void testInitHandlesEmptyList() {
        // Arrange
        when(insightsEngine.getClientGapInsights()).thenReturn(Collections.emptyList());

        // Act
        insightsBean.init();

        // Assert
        assertFalse(insightsBean.hasInsights());
        assertTrue(insightsBean.getInsights().isEmpty());
        assertNull(insightsBean.getErrorMessage());
    }

    @Test
    void testInitHandlesException() {
        // Arrange
        when(insightsEngine.getClientGapInsights())
                .thenThrow(new RuntimeException("Engine failure"));

        // Act
        insightsBean.init();

        // Assert
        assertFalse(insightsBean.hasInsights());
        assertTrue(insightsBean.getInsights().isEmpty());
        assertNotNull(insightsBean.getErrorMessage());
        assertTrue(insightsBean.getErrorMessage().contains("Engine failure"));
    }

    @Test
    void testRetryReloadsInsights() {
        // Arrange — first call throws, retry succeeds
        when(insightsEngine.getClientGapInsights())
                .thenThrow(new RuntimeException("First failure"))
                .thenReturn(Collections.singletonList(
                        InsightDTO.builder().sectorName("Finance").insightType("OPPORTUNITY").build()));

        // Act — initial load fails
        insightsBean.init();
        assertTrue(insightsBean.getErrorMessage().contains("First failure"));

        // Act — retry succeeds
        insightsBean.retry();

        // Assert
        assertTrue(insightsBean.hasInsights());
        assertEquals("Finance", insightsBean.getInsights().get(0).getSectorName());
        assertNull(insightsBean.getErrorMessage());
    }

    @Test
    void testGetInsightCssClass_ReturnsCorrectClasses() {
        // Arrange
        InsightDTO gap = InsightDTO.builder().insightType("GAP").build();
        InsightDTO opportunity = InsightDTO.builder().insightType("OPPORTUNITY").build();
        InsightDTO risk = InsightDTO.builder().insightType("RISK").build();
        InsightDTO unknown = InsightDTO.builder().insightType("UNKNOWN").build();
        InsightDTO nullType = InsightDTO.builder().insightType(null).build();

        // Act & Assert
        assertEquals("insight-card-gap", insightsBean.getInsightCssClass(gap));
        assertEquals("insight-card-opportunity", insightsBean.getInsightCssClass(opportunity));
        assertEquals("insight-card-risk", insightsBean.getInsightCssClass(risk));
        assertEquals("insight-card", insightsBean.getInsightCssClass(unknown));
        assertEquals("insight-card", insightsBean.getInsightCssClass(nullType));
        assertEquals("insight-card", insightsBean.getInsightCssClass(null));
    }
}
