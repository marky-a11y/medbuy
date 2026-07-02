package com.autoresolve.mediabuying.integration.llm;

import com.autoresolve.mediabuying.model.dto.InsightDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MockLlmInsightProviderTest {

    private MockLlmInsightProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockLlmInsightProvider();
    }

    @Test
    void testGapDetection_ClientCountEquals2() {
        // Arrange
        Map<String, LlmInsightProvider.SectorStats> sectors = new HashMap<>();
        sectors.put("Technology", new LlmInsightProvider.SectorStats(2, 50.0, 0, Map.of()));
        LlmInsightProvider.ClientAnalytics analytics = new LlmInsightProvider.ClientAnalytics(sectors);

        // Act
        List<InsightDTO> insights = provider.generateInsights(analytics);

        // Assert
        assertTrue(insights.stream().anyMatch(i -> "GAP".equals(i.getInsightType())));
        assertTrue(insights.stream().anyMatch(i -> i.getDetail().contains("only 2 active clients")));
    }

    @Test
    void testGapDetection_ClientCountGreaterThan2() {
        // Arrange
        Map<String, LlmInsightProvider.SectorStats> sectors = new HashMap<>();
        sectors.put("Finance", new LlmInsightProvider.SectorStats(5, 50.0, 0, Map.of()));
        LlmInsightProvider.ClientAnalytics analytics = new LlmInsightProvider.ClientAnalytics(sectors);

        // Act
        List<InsightDTO> insights = provider.generateInsights(analytics);

        // Assert — no GAP insight because clientCount > 2
        assertTrue(insights.stream().noneMatch(i -> "GAP".equals(i.getInsightType())));
    }

    @Test
    void testOpportunityDetection_AvgOutlookScoreAboveThreshold() {
        // Arrange
        Map<String, LlmInsightProvider.SectorStats> sectors = new HashMap<>();
        sectors.put("Healthcare", new LlmInsightProvider.SectorStats(10, 85.0, 0, Map.of()));
        LlmInsightProvider.ClientAnalytics analytics = new LlmInsightProvider.ClientAnalytics(sectors);

        // Act
        List<InsightDTO> insights = provider.generateInsights(analytics);

        // Assert
        assertTrue(insights.stream().anyMatch(i -> "OPPORTUNITY".equals(i.getInsightType())));
        assertTrue(insights.stream().anyMatch(i -> i.getDetail().contains("avg outlook 85/100")));
    }

    @Test
    void testOpportunityDetection_BelowThreshold() {
        // Arrange
        Map<String, LlmInsightProvider.SectorStats> sectors = new HashMap<>();
        sectors.put("Energy", new LlmInsightProvider.SectorStats(10, 50.0, 0, Map.of()));
        LlmInsightProvider.ClientAnalytics analytics = new LlmInsightProvider.ClientAnalytics(sectors);

        // Act
        List<InsightDTO> insights = provider.generateInsights(analytics);

        // Assert — no OPPORTUNITY insight because avg < 70
        assertTrue(insights.stream().noneMatch(i -> "OPPORTUNITY".equals(i.getInsightType())));
    }

    @Test
    void testRiskDetection_ExpiringContractsPresent() {
        // Arrange
        Map<String, LlmInsightProvider.SectorStats> sectors = new HashMap<>();
        sectors.put("Telecom", new LlmInsightProvider.SectorStats(5, 60.0, 2, Map.of()));
        LlmInsightProvider.ClientAnalytics analytics = new LlmInsightProvider.ClientAnalytics(sectors);

        // Act
        List<InsightDTO> insights = provider.generateInsights(analytics);

        // Assert
        assertTrue(insights.stream().anyMatch(i -> "RISK".equals(i.getInsightType())));
        assertTrue(insights.stream().anyMatch(i -> i.getDetail().contains("2 clients")));
    }

    @Test
    void testRiskDetection_NoExpiringContracts() {
        // Arrange
        Map<String, LlmInsightProvider.SectorStats> sectors = new HashMap<>();
        sectors.put("Education", new LlmInsightProvider.SectorStats(3, 60.0, 0, Map.of()));
        LlmInsightProvider.ClientAnalytics analytics = new LlmInsightProvider.ClientAnalytics(sectors);

        // Act
        List<InsightDTO> insights = provider.generateInsights(analytics);

        // Assert — no RISK insight
        assertTrue(insights.stream().noneMatch(i -> "RISK".equals(i.getInsightType())));
    }

    @Test
    void testMixedData_MultipleInsights() {
        // Arrange — triggers all three insight types
        Map<String, LlmInsightProvider.SectorStats> sectors = new HashMap<>();
        sectors.put("SmallGap", new LlmInsightProvider.SectorStats(1, 50.0, 0, Map.of()));
        sectors.put("HighGrowth", new LlmInsightProvider.SectorStats(10, 92.0, 0, Map.of()));
        sectors.put("AtRisk", new LlmInsightProvider.SectorStats(5, 65.0, 3, Map.of()));
        LlmInsightProvider.ClientAnalytics analytics = new LlmInsightProvider.ClientAnalytics(sectors);

        // Act
        List<InsightDTO> insights = provider.generateInsights(analytics);

        // Assert
        assertTrue(insights.stream().anyMatch(i -> "GAP".equals(i.getInsightType())));
        assertTrue(insights.stream().anyMatch(i -> "OPPORTUNITY".equals(i.getInsightType())));
        assertTrue(insights.stream().anyMatch(i -> "RISK".equals(i.getInsightType())));

        // At most one of each type per sector
        long gapCount = insights.stream().filter(i -> "GAP".equals(i.getInsightType())).count();
        long oppCount = insights.stream().filter(i -> "OPPORTUNITY".equals(i.getInsightType())).count();
        long riskCount = insights.stream().filter(i -> "RISK".equals(i.getInsightType())).count();
        assertEquals(1, gapCount);
        assertEquals(1, oppCount);
        assertEquals(1, riskCount);

        // Results sorted by confidenceScore descending
        for (int i = 1; i < insights.size(); i++) {
            assertTrue(insights.get(i - 1).getConfidenceScore() >= insights.get(i).getConfidenceScore());
        }
    }

    @Test
    void testEmptySectors() {
        // Arrange
        LlmInsightProvider.ClientAnalytics analytics = new LlmInsightProvider.ClientAnalytics(new HashMap<>());

        // Act
        List<InsightDTO> insights = provider.generateInsights(analytics);

        // Assert
        assertNotNull(insights);
        assertTrue(insights.isEmpty());
    }
}
