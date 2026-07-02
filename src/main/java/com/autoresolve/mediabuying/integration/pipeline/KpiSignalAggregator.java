package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.messaging.dto.CompanyPlatformMappingMessage;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 4a KPI signal aggregator that blends {@link CompanyPlatformMappingMessage}s
 * into a single {@link KPIMetrics} entity using weighted averages.
 * <p>
 * Weights are loaded from {@code kpi-synthesis.*} in {@code application.yml}.
 * For the MVP, mock KPI values are generated deterministically from each signal's
 * confidence score and the sector name, then blended using confidence weighting.
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "kpi-synthesis")
public class KpiSignalAggregator {

    private static final Logger log = LoggerFactory.getLogger(KpiSignalAggregator.class);

    // ---- ROAS sub-weights (must sum to 1.0) ----
    private double trendWeight = 0.3;
    private double searchVolumeWeight = 0.4;
    private double pricingWeight = 0.3;

    // ---- CAC sub-weights (must sum to 1.0) ----
    private double marketActivityWeight = 0.5;
    private double competitivenessWeight = 0.5;

    // ---- CLTV sub-weights (must sum to 1.0) ----
    private double demandWeight = 0.4;
    private double sentimentWeight = 0.3;
    private double footTrafficWeight = 0.3;

    // ---- Composite blending weights ----
    private double conversionRateWeight = 0.25;
    private double scalabilityWeight = 0.25;
    private double attributionAccuracyWeight = 0.25;
    /** Source name → list of KPI dimensions this source contributes to. */
    private Map<String, List<String>> sourceToKpi = new HashMap<>();

    /**
     * Composite trend weight — bound from {@code kpi-synthesis.attribute-trend-weight}
     * in application.yml (separate from the ROAS sub-weight {@code trendWeight}).
     */
    private double attributeTrendWeight = 0.25;

    // ========== Public API ==========

    /**
     * Aggregate a group of signals (all sharing the same company+sector+platform)
     * into a single {@link KPIMetrics} entity.
     *
     * @param signals   the signals belonging to the same group (never null)
     * @param sectorName the sector name for ID resolution
     * @return a fully populated KPIMetrics entity ready for upsert
     */
    public KPIMetrics aggregate(List<CompanyPlatformMappingMessage> signals, String sectorName) {
        if (signals == null || signals.isEmpty()) {
            log.warn("Empty signal list for sector '{}' — returning null", sectorName);
            return null;
        }

        // Use the first signal to determine platform/sector IDs
        CompanyPlatformMappingMessage first = signals.get(0);
        Long platformId = resolvePlatformId(first);
        Long sectorId = resolveSectorId(sectorName);

        // Compute confidence-weighted averages
        double totalWeight = 0.0;
        double weightedRoas = 0.0;
        double weightedCac = 0.0;
        double weightedCltv = 0.0;
        double weightedConversionRate = 0.0;
        double weightedScalability = 0.0;
        double weightedAttributionAccuracy = 0.0;

        for (CompanyPlatformMappingMessage signal : signals) {
            double confidence = signal.getConfidenceScore() != null
                    ? signal.getConfidenceScore()
                    : 0.5;
            totalWeight += confidence;

            // Generate mock per-signal KPI contributions
            double[] kpis = generateMockKpis(signal, sectorName);

            weightedRoas += confidence * kpis[0];
            weightedCac += confidence * kpis[1];
            weightedCltv += confidence * kpis[2];
            weightedConversionRate += confidence * kpis[3];
            weightedScalability += confidence * kpis[4];
            weightedAttributionAccuracy += confidence * kpis[5];
        }

        if (totalWeight <= 0.0) {
            log.warn("Total weight is zero for sector '{}' — using equal weights", sectorName);
            totalWeight = signals.size();
            weightedRoas = 0.0;
            weightedCac = 0.0;
            weightedCltv = 0.0;
            weightedConversionRate = 0.0;
            weightedScalability = 0.0;
            weightedAttributionAccuracy = 0.0;
            for (CompanyPlatformMappingMessage signal : signals) {
                double[] kpis = generateMockKpis(signal, sectorName);
                weightedRoas += kpis[0];
                weightedCac += kpis[1];
                weightedCltv += kpis[2];
                weightedConversionRate += kpis[3];
                weightedScalability += kpis[4];
                weightedAttributionAccuracy += kpis[5];
            }
        }

        // Build and return the KPIMetrics entity
        KPIMetrics metrics = KPIMetrics.builder()
                .platformId(platformId)
                .sectorId(sectorId)
                .roas(round(weightedRoas / totalWeight, 2))
                .cac(round(weightedCac / totalWeight, 2))
                .cltv(round(weightedCltv / totalWeight, 2))
                .conversionRate(round(weightedConversionRate / totalWeight, 4))
                .scalability(round(weightedScalability / totalWeight, 2))
                .attributionAccuracy(round(weightedAttributionAccuracy / totalWeight, 4))
                .contributionMargin(round((weightedRoas / totalWeight) * 0.3, 2))
                .paybackPeriod(round((weightedCac / totalWeight) / 5.0, 2))
                .incrementalReturn(round((weightedRoas / totalWeight) * 1.5, 2))
                .costPerQualifiedLead(round((weightedCac / totalWeight) * 0.6, 2))
                .cashConversionCycle(round(30.0 + (weightedCac / totalWeight) * 0.5, 2))
                .saturationPoint(round(0.05 + (weightedRoas / totalWeight) * 0.01, 4))
                .ingestionTimestamp(Instant.now())
                .dataSource(first.getMappingMethod())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        log.debug("Aggregated {} signal(s) into KPIMetrics for platform={} sector={}: " +
                        "ROAS={} CAC={} CLTV={} ConvRate={} Scalability={} AttrAcc={}",
                signals.size(), platformId, sectorId,
                metrics.getRoas(), metrics.getCac(), metrics.getCltv(),
                metrics.getConversionRate(), metrics.getScalability(),
                metrics.getAttributionAccuracy());

        return metrics;
    }

    // ========== Mock KPI generation (deterministic) ==========

    /**
     * Generate a deterministic mock KPI vector from a single signal.
     * <p>
     * Uses a hash of (companyName + sectorName) to produce reproducible values
     * within the required MVP ranges, scaled by confidence.
     * </p>
     *
     * @return double[6] — {roas, cac, cltv, conversionRate, scalability, attributionAccuracy}
     */
    double[] generateMockKpis(CompanyPlatformMappingMessage signal, String sectorName) {
        String seed = (signal.getCompanyName() != null ? signal.getCompanyName() : "default")
                + "|" + (sectorName != null ? sectorName : "unknown");
        double hash = Math.abs((long) seed.hashCode() % 10000) / 10000.0;

        double confidence = signal.getConfidenceScore() != null
                ? Math.min(1.0, Math.max(0.1, signal.getConfidenceScore()))
                : 0.5;

        // Base values per sector (deterministic from hash)
        double baseRoas = 1.5 + hash * 4.5;               // range 1.5–6.0
        double baseCac = 10.0 + hash * 70.0;               // range 10–80
        double baseCltv = 100.0 + hash * 700.0;             // range 100–800
        double baseConversion = 0.02 + hash * 0.13;         // range 0.02–0.15
        double baseScalability = 3.0 + hash * 6.0;          // range 3–9
        double baseAttribution = 0.4 + hash * 0.5;           // range 0.4–0.9

        // Adjust by confidence: higher confidence → more optimistic ROAS, CLTV, etc.
        double confidenceFactor = 0.5 + 0.5 * confidence;

        return new double[]{
                baseRoas * confidenceFactor,
                baseCac * (2.0 - confidenceFactor),        // higher confidence → lower CAC
                baseCltv * confidenceFactor,
                baseConversion * confidenceFactor,
                baseScalability * confidenceFactor,
                baseAttribution * (0.5 + 0.5 * confidenceFactor)
        };
    }

    // ========== ID resolvers (MVP deterministic) ==========

    static Long resolveSectorId(String sectorName) {
        if (sectorName == null) {
            return 0L;
        }
        switch (sectorName.toLowerCase().trim()) {
            case "technology":
                return 1L;
            case "finance":
                return 2L;
            case "manufacturing":
                return 3L;
            case "retail":
                return 4L;
            case "health-wellness":
                return 5L;
            case "travel":
                return 6L;
            case "job-market":
                return 7L;
            case "local-business":
                return 8L;
            default:
                return 0L;
        }
    }

    static Long resolvePlatformId(CompanyPlatformMappingMessage msg) {
        if (msg == null) {
            return 0L;
        }
        List<String> platforms = msg.getInferredAdPlatforms();
        if (platforms == null || platforms.isEmpty()) {
            return 0L;
        }
        return resolvePlatformId(platforms.get(0));
    }

    static Long resolvePlatformId(String platformName) {
        if (platformName == null) {
            return 0L;
        }
        // IDs must match the auto-increment IDs from V6__seed_data.sql + new additions.
        switch (platformName.toLowerCase().trim()) {
            case "google_ads":
                return 1L;
            case "meta_ads":
                return 2L;
            case "tiktok_ads":
                return 3L;
            case "linkedin_ads":
                return 4L;
            case "iheart_radio":
                return 5L;
            case "google_shopping":
                return 6L;
            case "yelp_ads":
                return 7L;
            case "foursquare_ads":
                return 8L;
            case "bing_ads":
                return 9L;
            case "skyscanner_ads":
                return 10L;
            default:
                return 0L;
        }
    }

    // ========== Property accessors (for @ConfigurationProperties) ==========

    public double getTrendWeight() {
        return trendWeight;
    }

    public void setTrendWeight(double trendWeight) {
        this.trendWeight = trendWeight;
    }

    public double getSearchVolumeWeight() {
        return searchVolumeWeight;
    }

    public void setSearchVolumeWeight(double searchVolumeWeight) {
        this.searchVolumeWeight = searchVolumeWeight;
    }

    public double getPricingWeight() {
        return pricingWeight;
    }

    public void setPricingWeight(double pricingWeight) {
        this.pricingWeight = pricingWeight;
    }

    public double getMarketActivityWeight() {
        return marketActivityWeight;
    }

    public void setMarketActivityWeight(double marketActivityWeight) {
        this.marketActivityWeight = marketActivityWeight;
    }

    public double getCompetitivenessWeight() {
        return competitivenessWeight;
    }

    public void setCompetitivenessWeight(double competitivenessWeight) {
        this.competitivenessWeight = competitivenessWeight;
    }

    public double getDemandWeight() {
        return demandWeight;
    }

    public void setDemandWeight(double demandWeight) {
        this.demandWeight = demandWeight;
    }

    public double getSentimentWeight() {
        return sentimentWeight;
    }

    public void setSentimentWeight(double sentimentWeight) {
        this.sentimentWeight = sentimentWeight;
    }

    public double getFootTrafficWeight() {
        return footTrafficWeight;
    }

    public void setFootTrafficWeight(double footTrafficWeight) {
        this.footTrafficWeight = footTrafficWeight;
    }

    public double getConversionRateWeight() {
        return conversionRateWeight;
    }

    public void setConversionRateWeight(double conversionRateWeight) {
        this.conversionRateWeight = conversionRateWeight;
    }

    public double getScalabilityWeight() {
        return scalabilityWeight;
    }

    public void setScalabilityWeight(double scalabilityWeight) {
        this.scalabilityWeight = scalabilityWeight;
    }

    public double getAttributionAccuracyWeight() {
        return attributionAccuracyWeight;
    }

    public void setAttributionAccuracyWeight(double attributionAccuracyWeight) {
        this.attributionAccuracyWeight = attributionAccuracyWeight;
    }

    public double getAttributeTrendWeight() {
        return attributeTrendWeight;
    }

    public void setAttributeTrendWeight(double attributeTrendWeight) {
        this.attributeTrendWeight = attributeTrendWeight;
    }

    public Map<String, List<String>> getSourceToKpi() {
        return sourceToKpi;
    }

    public void setSourceToKpi(Map<String, List<String>> sourceToKpi) {
        this.sourceToKpi = sourceToKpi;
    }

    // ========== Internal helpers ==========

    private static BigDecimal round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
