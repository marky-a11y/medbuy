package com.autoresolve.mediabuying.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Client-level insight metrics used to evaluate and recommend client companies
 * in the Top Client Prospects recommendation dialog.
 * <p>
 * These metrics are populated from mock data when live API data is not available.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientInsightMetricsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Average Yelp rating (1.0 – 5.0) */
    private Double avgYelpRating;

    /** Number of Yelp reviews */
    private Integer yelpReviewCount;

    /** Competitor review gap — difference in review count vs top competitor */
    private Integer competitorReviewGap;

    /** Business completeness score (0 – 100) */
    private Integer businessCompleteness;

    /** Whether the business has a website */
    private Boolean websitePresent;

    /** Website quality score from Google PageSpeed Insights (0 – 100) */
    private Integer websiteQualityScore;

    /** Whether the business responds to reviews */
    private Boolean respondsToReviews;

    /** Years in business */
    private Integer yearsInBusiness;

    /** Competitor density score (0 – 100, higher = more competitors) */
    private Integer competitorDensity;

    /** Local search demand score from Google Trends (0 – 100) */
    private Integer localSearchDemand;

    /** Median household income in the company's demographic area (USD) */
    private Double medianHouseholdIncome;

    /** Population growth rate in the company's demographic area (percentage) */
    private Double populationGrowth;
}
