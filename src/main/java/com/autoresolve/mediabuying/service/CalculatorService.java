package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.dto.CalculatorInputDTO;
import com.autoresolve.mediabuying.model.dto.CalculatorResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CalculatorService {

    private static final Logger log = LoggerFactory.getLogger(CalculatorService.class);

    public CalculatorResultDTO compute(CalculatorInputDTO input) {
        double spend = input.getMonthlyAdSpend();
        double cpc = input.getCostPerClick();
        double ctr = input.getClickThroughRate();
        double clickToLead = input.getClickToLeadRate();
        double leadToCustomer = input.getLeadToCustomerRate();
        double monthlyRevenue = input.getMonthlyRevenuePerCustomer();
        int retentionMonths = input.getAverageRetentionMonths();
        double margin = input.getGrossMargin();

        // --- Funnel Math ---
        int clicks = (int) Math.round(spend / cpc);
        
        // Estimate impressions from CTR: impressions = clicks / CTR
        int impressions = ctr > 0 ? (int) Math.round(clicks / ctr) : 0;
        
        int leads = (int) Math.round(clicks * clickToLead);
        int customers = (int) Math.round(leads * leadToCustomer);
        
        // Revenue
        double ltv = monthlyRevenue * retentionMonths * margin;
        double revenue = customers * ltv;
        
        // ROI metrics
        double roas = spend > 0 ? revenue / spend : 0;
        double cac = customers > 0 ? spend / customers : 0;
        double ltvToCac = cac > 0 ? ltv / cac : 0;

        // --- Sensitivity Analysis ---
        // Low case: -30% on conversion rates, +20% on CPC
        double lowClicks = spend / (cpc * 1.2);
        double lowLeads = lowClicks * clickToLead * 0.7;
        double lowCustomers = lowLeads * leadToCustomer * 0.7;
        double lowRevenue = lowCustomers * ltv;
        double lowRoi = spend > 0 ? lowRevenue / spend : 0;

        // High case: +30% on conversion rates, -15% on CPC
        double highClicks = spend / (cpc * 0.85);
        double highLeads = highClicks * clickToLead * 1.3;
        double highCustomers = highLeads * leadToCustomer * 1.3;
        double highRevenue = highCustomers * ltv;
        double highRoi = spend > 0 ? highRevenue / spend : 0;

        // --- Confidence ---
        String confidence = calculateConfidence(input);

        // --- Business type label ---
        String businessTypeLabel = getBusinessTypeLabel(input.getBusinessType());

        CalculatorResultDTO result = CalculatorResultDTO.builder()
                .impressions(impressions)
                .clicks(clicks)
                .leads(leads)
                .customers(customers)
                .revenue(round(revenue, 2))
                .adSpend(spend)
                .roas(round(roas, 2))
                .cac(round(cac, 2))
                .ltv(round(ltv, 2))
                .ltvToCac(round(ltvToCac, 2))
                .clickToLeadRate(clickToLead)
                .leadToCustomerRate(leadToCustomer)
                .grossMargin(margin)
                .averageRetentionMonths(retentionMonths)
                .monthlyRevenuePerCustomer(monthlyRevenue)
                .lowCaseRoi(round(lowRoi, 2))
                .highCaseRoi(round(highRoi, 2))
                .lowCaseRevenue(round(lowRevenue, 2))
                .highCaseRevenue(round(highRevenue, 2))
                .confidence(confidence)
                .businessType(input.getBusinessType())
                .businessTypeLabel(businessTypeLabel)
                .build();

        log.debug("Yelp funnel ROI computed: spend={} roas={} cac={} ltv={} ltv:cac={}",
                spend, result.getRoas(), result.getCac(), result.getLtv(), result.getLtvToCac());

        return result;
    }

    private String calculateConfidence(CalculatorInputDTO input) {
        int filled = 0;
        if (input.getClickThroughRate() != null && input.getClickThroughRate() > 0) filled++;
        if (input.getCostPerClick() != null && input.getCostPerClick() > 0) filled++;
        if (input.getClickToLeadRate() != null && input.getClickToLeadRate() > 0) filled++;
        if (input.getLeadToCustomerRate() != null && input.getLeadToCustomerRate() > 0) filled++;
        if (input.getMonthlyRevenuePerCustomer() != null && input.getMonthlyRevenuePerCustomer() > 0) filled++;
        if (input.getAverageRetentionMonths() != null && input.getAverageRetentionMonths() > 0) filled++;
        if (input.getGrossMargin() != null && input.getGrossMargin() > 0) filled++;
        if (input.getBusinessType() != null && !input.getBusinessType().isEmpty()) filled++;
        if (input.getPopulationDensity() != null) filled++;
        if (input.getIncomeLevel() != null) filled++;
        if (input.getCompetitorSaturation() != null) filled++;
        if (input.getYelpRating() != null) filled++;

        if (filled >= 10) return "High";
        if (filled >= 6) return "Medium";
        return "Low";
    }

    private String getBusinessTypeLabel(String type) {
        if (type == null) return "General Business";
        switch (type) {
            case "gym": return "Gym / Fitness Center";
            case "personal_training": return "Personal Training";
            case "yoga": return "Yoga / Pilates Studio";
            case "crossfit": return "CrossFit Box";
            default: return "Other Business";
        }
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
