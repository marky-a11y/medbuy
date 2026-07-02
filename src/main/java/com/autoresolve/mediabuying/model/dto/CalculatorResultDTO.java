package com.autoresolve.mediabuying.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatorResultDTO {
    // Funnel metrics
    private int impressions;
    private int clicks;
    private int leads;
    private int customers;
    private double revenue;
    private double adSpend;

    // ROI metrics
    private double roas;            // Return on Ad Spend (revenue / spend)
    private double cac;             // Customer Acquisition Cost
    private double ltv;             // Customer Lifetime Value
    private double ltvToCac;        // LTV:CAC ratio

    // Funnel rates (for display)
    private double clickToLeadRate;
    private double leadToCustomerRate;
    private double grossMargin;
    private int averageRetentionMonths;
    private double monthlyRevenuePerCustomer;

    // Sensitivity analysis
    private double lowCaseRoi;
    private double highCaseRoi;
    private double lowCaseRevenue;
    private double highCaseRevenue;

    // Confidence
    private String confidence;      // "Low", "Medium", "High"

    // Business type
    private String businessType;
    private String businessTypeLabel;
}
