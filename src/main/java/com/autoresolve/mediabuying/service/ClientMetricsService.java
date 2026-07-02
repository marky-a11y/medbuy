package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.dto.ClientInsightMetricsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Provides client-level insight metrics for the recommendation dialog.
 * <p>
 * Uses mock data when live API integrations are not available.
 * Metrics are keyed by company name (optionally scoped to industry vertical).
 * </p>
 */
@Service
public class ClientMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ClientMetricsService.class);

    /**
     * Returns client insight metrics for a given company and its industry vertical.
     * If company-specific data is not available, falls back to industry averages.
     *
     * @param companyName     the client prospect company name
     * @param industryVertical the industry vertical (e.g. "technology", "health-wellness")
     * @return populated metrics DTO
     */
    public ClientInsightMetricsDTO getMetrics(String companyName, String industryVertical) {
        // Try company-specific data first
        ClientInsightMetricsDTO specific = getCompanyMetrics(companyName);
        if (specific != null) {
            return specific;
        }
        // Fall back to industry vertical defaults
        return getIndustryVerticalMetrics(industryVertical, companyName);
    }

    /**
     * Returns company-specific metrics (from mock data).
     */
    private ClientInsightMetricsDTO getCompanyMetrics(String companyName) {
        if (companyName == null) return null;
        String key = companyName.trim().toLowerCase();

        // Company-specific mock data
        Map<String, ClientInsightMetricsDTO> companyMetrics = buildCompanyMetrics();
        return companyMetrics.get(key);
    }

    /**
     * Returns metrics based on industry vertical, with some randomization per company.
     */
    private ClientInsightMetricsDTO getIndustryVerticalMetrics(String industryVertical, String companyName) {
        String key = industryVertical != null ? industryVertical.trim().toLowerCase() : "default";

        // Base metrics per industry vertical
        Map<String, double[]> verticalBases = buildVerticalBases();
        double[] base = verticalBases.getOrDefault(key, verticalBases.get("default"));

        // Add some randomization so each company gets slightly different values
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double seed = companyName != null ? Math.abs(companyName.hashCode() % 37) / 37.0 : 0.5;

        return ClientInsightMetricsDTO.builder()
                .avgYelpRating(clamp(base[0] + rng.nextDouble(-0.3, 0.3), 1.0, 5.0))
                .yelpReviewCount((int) Math.round(base[1] + rng.nextDouble(-base[1] * 0.2, base[1] * 0.2)))
                .competitorReviewGap((int) Math.round(base[2] + rng.nextDouble(-20, 20)))
                .businessCompleteness((int) Math.round(clamp(base[3] + rng.nextDouble(-10, 10), 0, 100)))
                .websitePresent(rng.nextDouble() < 0.85)
                .websiteQualityScore((int) Math.round(clamp(base[4] + rng.nextDouble(-10, 10), 0, 100)))
                .respondsToReviews(rng.nextDouble() < 0.65)
                .yearsInBusiness((int) Math.round(base[5] + rng.nextDouble(-3, 3)))
                .competitorDensity((int) Math.round(clamp(base[6] + rng.nextDouble(-10, 10), 0, 100)))
                .localSearchDemand((int) Math.round(clamp(base[7] + rng.nextDouble(-10, 10), 0, 100)))
                .medianHouseholdIncome(base[8] + rng.nextDouble(-10000, 10000))
                .populationGrowth(clamp(base[9] + rng.nextDouble(-1.0, 1.0), -5.0, 10.0))
                .build();
    }

    /**
     * Company-specific mock data.
     */
    private Map<String, ClientInsightMetricsDTO> buildCompanyMetrics() {
        Map<String, ClientInsightMetricsDTO> map = new HashMap<>();

        map.put("acme corp", ClientInsightMetricsDTO.builder()
                .avgYelpRating(4.2).yelpReviewCount(87).competitorReviewGap(12)
                .businessCompleteness(92).websitePresent(true).websiteQualityScore(78)
                .respondsToReviews(true).yearsInBusiness(12).competitorDensity(45)
                .localSearchDemand(68).medianHouseholdIncome(72000.0).populationGrowth(2.1)
                .build());

        map.put("globex inc", ClientInsightMetricsDTO.builder()
                .avgYelpRating(3.8).yelpReviewCount(45).competitorReviewGap(-8)
                .businessCompleteness(65).websitePresent(true).websiteQualityScore(55)
                .respondsToReviews(false).yearsInBusiness(6).competitorDensity(72)
                .localSearchDemand(42).medianHouseholdIncome(58000.0).populationGrowth(1.5)
                .build());

        map.put("initech solutions", ClientInsightMetricsDTO.builder()
                .avgYelpRating(4.5).yelpReviewCount(210).competitorReviewGap(34)
                .businessCompleteness(88).websitePresent(true).websiteQualityScore(92)
                .respondsToReviews(true).yearsInBusiness(18).competitorDensity(38)
                .localSearchDemand(85).medianHouseholdIncome(95000.0).populationGrowth(3.2)
                .build());

        map.put("umbrella co", ClientInsightMetricsDTO.builder()
                .avgYelpRating(3.2).yelpReviewCount(23).competitorReviewGap(-35)
                .businessCompleteness(45).websitePresent(false).websiteQualityScore(0)
                .respondsToReviews(false).yearsInBusiness(3).competitorDensity(88)
                .localSearchDemand(22).medianHouseholdIncome(42000.0).populationGrowth(0.8)
                .build());

        map.put("hooli", ClientInsightMetricsDTO.builder()
                .avgYelpRating(4.8).yelpReviewCount(340).competitorReviewGap(62)
                .businessCompleteness(97).websitePresent(true).websiteQualityScore(95)
                .respondsToReviews(true).yearsInBusiness(22).competitorDensity(25)
                .localSearchDemand(92).medianHouseholdIncome(112000.0).populationGrowth(4.5)
                .build());

        return map;
    }

    /**
     * Base metric values per industry vertical.
     * Order: avgYelpRating, yelpReviewCount, competitorReviewGap, businessCompleteness,
     *        websiteQualityScore, yearsInBusiness, competitorDensity, localSearchDemand,
     *        medianHouseholdIncome, populationGrowth
     */
    private Map<String, double[]> buildVerticalBases() {
        Map<String, double[]> map = new HashMap<>();
        map.put("technology",       new double[]{4.2, 120,  15, 85, 75, 10, 40, 70, 95000,  3.5});
        map.put("finance",          new double[]{3.8,  80,  10, 78, 65, 15, 55, 55, 110000, 2.8});
        map.put("retail",           new double[]{4.0, 200,  25, 70, 60,  8, 60, 65, 62000,  2.0});
        map.put("health-wellness",  new double[]{4.5, 150,  20, 82, 55,  7, 45, 58, 68000,  2.5});
        map.put("travel",           new double[]{4.1, 175,  18, 75, 70, 12, 50, 62, 74000,  3.0});
        map.put("manufacturing",    new double[]{3.5,  40,   5, 72, 45, 20, 35, 35, 78000,  1.5});
        map.put("job-market",       new double[]{3.6,  60,   8, 68, 60,  9, 48, 45, 65000,  2.2});
        map.put("default",          new double[]{3.8, 100,  12, 75, 60, 10, 50, 50, 70000,  2.0});
        return map;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
