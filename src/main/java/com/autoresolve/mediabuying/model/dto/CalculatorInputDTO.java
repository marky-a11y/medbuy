package com.autoresolve.mediabuying.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatorInputDTO {

    // Core ad assumptions
    @NotNull @DecimalMin("0.01") private Double monthlyAdSpend;   // Monthly ad spend in $
    @NotNull @DecimalMin("0.01") private Double costPerClick;     // CPC in $
    @NotNull @DecimalMin("0.0") private Double clickThroughRate;  // CTR as decimal (0.05 = 5%)

    // Funnel conversion rates
    @NotNull @DecimalMin("0.0") private Double clickToLeadRate;       // Click → Lead conversion (0.12 = 12%)
    @NotNull @DecimalMin("0.0") private Double leadToCustomerRate;    // Lead → Paying customer (0.30 = 30%)

    // Revenue assumptions
    @NotNull @DecimalMin("0.01") private Double monthlyRevenuePerCustomer; // Avg monthly revenue per customer
    @NotNull @Min(1) private Integer averageRetentionMonths;         // How many months customer stays
    @NotNull @DecimalMin("0.0") private Double grossMargin;          // Gross margin as decimal (0.70 = 70%)

    // Business segmentation
    private String businessType;   // "gym", "personal_training", "yoga", "crossfit", "other"

    // Local market factors (optional — default to industry averages)
    private Double populationDensity;       // Relative score 0.0-1.0
    private Double incomeLevel;             // Relative score 0.0-1.0
    private Double competitorSaturation;    // Relative score 0.0-1.0
    private Double yelpRating;              // Average Yelp rating (1.0-5.0)
}
