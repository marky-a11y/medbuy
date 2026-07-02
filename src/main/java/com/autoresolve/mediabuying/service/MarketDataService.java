package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.dto.MarketEstimatesDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Provides market benchmark estimates based on platform, sector, business type, and city.
 * Uses industry data and public benchmarks to pre-populate the ROI calculator.
 * Falls back to reasonable defaults when specific data is not available.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private static final List<String> US_CITIES = Arrays.asList(
            "New York, NY", "Los Angeles, CA", "Chicago, IL", "Houston, TX",
            "Phoenix, AZ", "Philadelphia, PA", "San Antonio, TX", "San Diego, CA",
            "Dallas, TX", "San Jose, CA", "Austin, TX", "Jacksonville, FL",
            "Fort Worth, TX", "Columbus, OH", "Charlotte, NC", "Indianapolis, IN",
            "San Francisco, CA", "Seattle, WA", "Denver, CO", "Nashville, TN",
            "Portland, OR", "Miami, FL", "Atlanta, GA", "Boston, MA"
    );

    public List<String> getUsCities() {
        return US_CITIES;
    }

    /**
     * Returns market estimates for the given selections.
     * Values are from industry benchmarks adjusted by city and platform.
     */
    public MarketEstimatesDTO getEstimates(String platform, String sector, String businessType, String city) {
        // CPC by platform
        double baseCpc = getBaseCpc(platform);
        // Adjust by city cost index
        double cityFactor = getCityCostFactor(city);
        double cpc = round(baseCpc * cityFactor, 2);

        // CTR by sector
        double ctr = getCtrBySector(sector);

        // Funnel rates by business type
        double clickToLead = getClickToLeadRate(businessType);
        double leadToCustomer = getLeadToCustomerRate(businessType);

        // Revenue by sector + business type
        double monthlyRevenue = getMonthlyRevenue(sector, businessType);
        int retentionMonths = getRetentionMonths(businessType);
        double grossMargin = getGrossMargin(sector);

        // Format source description
        String sourceDesc = String.format("Industry benchmarks for %s / %s / %s, adjusted for %s",
                platform != null ? platform : "general",
                sector != null ? sector : "general",
                businessType != null ? businessType : "general",
                city != null ? city : "national average");

        MarketEstimatesDTO est = new MarketEstimatesDTO(
                cpc, ctr, clickToLead, leadToCustomer,
                monthlyRevenue, retentionMonths, grossMargin, sourceDesc);

        log.debug("Market estimates for platform={} sector={} bizType={} city={}: CPC={} CTR={} C→L={} L→C={} Rev={} Ret={} Margin={}",
                platform, sector, businessType, city, cpc, ctr, clickToLead, leadToCustomer,
                monthlyRevenue, retentionMonths, grossMargin);

        return est;
    }

    private double getBaseCpc(String platform) {
        if (platform == null) return 2.50;
        switch (platform.toLowerCase()) {
            case "google_ads":     return 2.50;
            case "meta_ads":       return 1.80;
            case "linkedin_ads":   return 5.50;
            case "tiktok_ads":     return 1.20;
            case "yelp_ads":       return 5.00;
            case "bing_ads":       return 2.00;
            default:               return 2.50;
        }
    }

    private double getCityCostFactor(String city) {
        if (city == null) return 1.0;
        switch (city.toLowerCase().replaceAll("[^a-z]", "")) {
            case "newyork":  case "sanfrancisco":  case "boston":
            case "losangeles": return 1.4;
            case "seattle": case "washingtondc": case "miami":
            case "sandiego": return 1.25;
            case "chicago": case "denver": case "austin":
            case "portland": return 1.15;
            case "dallas": case "atlanta": case "phoenix":
            case "nashville": return 1.05;
            default: return 1.0;
        }
    }

    private double getCtrBySector(String sector) {
        if (sector == null) return 0.05;
        switch (sector.toLowerCase()) {
            case "technology":       return 0.045;
            case "finance":          return 0.035;
            case "retail":           return 0.055;
            case "health-wellness":  return 0.05;
            case "travel":           return 0.04;
            case "manufacturing":    return 0.03;
            case "job-market":       return 0.06;
            default:                 return 0.045;
        }
    }

    private double getClickToLeadRate(String businessType) {
        if (businessType == null) return 0.10;
        switch (businessType) {
            case "gym":               return 0.12;
            case "personal_training":  return 0.10;
            case "yoga":              return 0.14;
            case "crossfit":          return 0.11;
            default:                  return 0.10;
        }
    }

    private double getLeadToCustomerRate(String businessType) {
        if (businessType == null) return 0.25;
        switch (businessType) {
            case "gym":               return 0.30;
            case "personal_training":  return 0.35;
            case "yoga":              return 0.28;
            case "crossfit":          return 0.32;
            default:                  return 0.25;
        }
    }

    private double getMonthlyRevenue(String sector, String businessType) {
        if (businessType != null) {
            switch (businessType) {
                case "gym":               return 80.0;
                case "personal_training":  return 200.0;
                case "yoga":              return 120.0;
                case "crossfit":          return 150.0;
            }
        }
        if (sector == null) return 100.0;
        switch (sector.toLowerCase()) {
            case "technology":       return 150.0;
            case "finance":          return 200.0;
            case "retail":           return 80.0;
            case "health-wellness":  return 100.0;
            case "travel":           return 120.0;
            default:                 return 100.0;
        }
    }

    private int getRetentionMonths(String businessType) {
        if (businessType == null) return 6;
        switch (businessType) {
            case "gym":               return 8;
            case "personal_training":  return 6;
            case "yoga":              return 9;
            case "crossfit":          return 10;
            default:                  return 6;
        }
    }

    private double getGrossMargin(String sector) {
        if (sector == null) return 0.65;
        switch (sector.toLowerCase()) {
            case "technology":       return 0.75;
            case "finance":          return 0.70;
            case "retail":           return 0.55;
            case "health-wellness":  return 0.70;
            case "travel":           return 0.60;
            default:                 return 0.65;
        }
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
