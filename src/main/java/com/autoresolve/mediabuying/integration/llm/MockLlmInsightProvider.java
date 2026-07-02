package com.autoresolve.mediabuying.integration.llm;

import com.autoresolve.mediabuying.model.dto.InsightDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Mock implementation of {@link LlmInsightProvider} that uses deterministic
 * rules to generate insights per sector. Used when no real LLM backend is configured.
 *
 * <p>Rules applied per sector:
 * <ul>
 *   <li><b>GAP</b> — clientCount &lt;= 2 → "Sector X has only N active clients"</li>
 *   <li><b>OPPORTUNITY</b> — avgOutlookScore &gt;= 70 → "Sector X shows strong growth potential"</li>
 *   <li><b>RISK</b> — expiringContractCount &gt;= 1 → "N clients in Sector X have contracts expiring soon"</li>
 * </ul>
 *
 * <p>At most one insight of each type per sector. Results sorted by confidenceScore descending.
 */
@Component
public class MockLlmInsightProvider implements LlmInsightProvider {

    private static final Logger log = LoggerFactory.getLogger(MockLlmInsightProvider.class);

    @Override
    public List<InsightDTO> generateInsights(ClientAnalytics analytics) {
        log.debug("Generating mock insights for {} sectors", analytics.getSectors().size());

        List<InsightDTO> insights = new ArrayList<>();

        for (Map.Entry<String, SectorStats> entry : analytics.getSectors().entrySet()) {
            String sectorName = entry.getKey();
            SectorStats stats = entry.getValue();

            // GAP: clientCount <= 2
            if (stats.getClientCount() <= 2) {
                String headline = String.format("Prospecting gap in %s", sectorName);
                String detail = String.format(
                        "%s currently has only %d active client%s in our portfolio, representing a significant prospecting gap. " +
                        "The sector shows growing advertiser demand with increasing search volume and competitive CPCs, " +
                        "making it an attractive target for new client acquisition. " +
                        "Recommended action: Launch targeted outreach campaigns focusing on mid-market %s businesses " +
                        "that are currently underserved by their existing ad agencies. " +
                        "Prioritize companies with strong Yelp ratings (4.0+) and established web presence, " +
                        "as these indicators correlate with higher ad budget readiness and campaign success rates.",
                        sectorName, stats.getClientCount(), stats.getClientCount() == 1 ? "" : "s",
                        sectorName.toLowerCase());
                insights.add(InsightDTO.builder()
                        .sectorName(sectorName)
                        .insightType("GAP")
                        .headline(headline)
                        .detail(detail)
                        .confidenceScore(0.85)
                        .build());
            }

            // OPPORTUNITY: avgOutlookScore >= 70
            if (stats.getAvgOutlookScore() >= 70) {
                String headline = String.format("Strong growth potential in %s", sectorName);
                String detail = String.format(
                        "%s demonstrates strong growth potential with an average client outlook score of %.0f/100, " +
                        "well above the portfolio average. The sector's strong performance is driven by favorable market conditions: " +
                        "rising local search demand (+16%% YoY), manageable competitor density (moderate), and healthy " +
                        "customer lifetime values relative to acquisition costs. " +
                        "Why explore this opportunity: Companies in %s typically see 25-40%% higher ROAS compared to other sectors " +
                        "due to well-defined purchase intent and shorter conversion cycles. " +
                        "Recommended action: Increase ad spend allocation by 15-20%% for existing %s clients and expand " +
                        "into adjacent sub-sectors (specialty services, premium offerings) where competitor saturation is lower. " +
                        "Consider testing new ad formats (video, local service ads) that can further reduce CAC while maintaining " +
                        "current conversion rates.",
                        sectorName, stats.getAvgOutlookScore(),
                        sectorName, sectorName);
                insights.add(InsightDTO.builder()
                        .sectorName(sectorName)
                        .insightType("OPPORTUNITY")
                        .headline(headline)
                        .detail(detail)
                        .confidenceScore(0.78)
                        .build());
            }

            // RISK: expiringContractCount >= 1
            if (stats.getExpiringContractCount() >= 1) {
                String headline = String.format("Contract renewals at risk in %s", sectorName);
                String detail = String.format(
                        "%d client%s in %s ha%s contract%s expiring within the next quarter, " +
                        "representing a material retention risk that requires immediate attention. " +
                        "Historical renewal patterns show that proactive outreach 60-90 days before expiration " +
                        "improves retention rates by approximately 35%% compared to last-minute renewals. " +
                        "Why this matters: Each retained client in %s generates an average LTV:CAC ratio of 8:1 or higher, " +
                        "making retention significantly more cost-effective than new acquisition. " +
                        "Recommended action: Schedule renewal meetings immediately for each at-risk account, " +
                        "prepare performance summaries highlighting achieved ROAS improvements and cost savings, " +
                        "and consider offering tiered renewal incentives (locked-in rates for multi-year commitments). " +
                        "Assign dedicated account managers to high-value accounts to ensure personalized attention " +
                        "during the renewal process.",
                        stats.getExpiringContractCount(),
                        stats.getExpiringContractCount() == 1 ? "" : "s",
                        sectorName,
                        stats.getExpiringContractCount() == 1 ? "s a" : "ve",
                        stats.getExpiringContractCount() == 1 ? "" : "s",
                        sectorName);
                insights.add(InsightDTO.builder()
                        .sectorName(sectorName)
                        .insightType("RISK")
                        .headline(headline)
                        .detail(detail)
                        .confidenceScore(0.72)
                        .build());
            }
        }

        // Sort by confidenceScore descending
        insights.sort(Comparator.comparing(InsightDTO::getConfidenceScore).reversed());

        log.debug("Generated {} mock insights", insights.size());
        return insights;
    }
}
