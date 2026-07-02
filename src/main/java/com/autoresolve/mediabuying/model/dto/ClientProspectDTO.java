package com.autoresolve.mediabuying.model.dto;

import com.autoresolve.mediabuying.model.entity.ClientProspect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProspectDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String companyName;
    private Double estAnnualRevenue;
    private String estAnnualRevenueFormatted;
    private Double yoyGrowthRate;
    private String yoyGrowthFormatted;
    private String growthDirection; // "up", "down", "flat"
    private Double estAdBudget;
    private String estAdBudgetFormatted;
    private String industryVertical;
    private Integer sectorFitScore;

    // Client-level KPI metrics (populated by ClientMetricsService)
    private Double avgYelpRating;
    private Integer websiteQualityScore;
    private Integer competitorDensity;

    /**
     * Factory method that builds a DTO from a ClientProspect entity and a computed fit score.
     */
    public static ClientProspectDTO from(ClientProspect entity, int sectorFitScore) {
        double revenue = entity.getEstAnnualRevenue() != null ? entity.getEstAnnualRevenue().doubleValue() : 0.0;
        double growth = entity.getYoyGrowthRate() != null ? entity.getYoyGrowthRate().doubleValue() : 0.0;
        double budget = entity.getEstAdBudget() != null ? entity.getEstAdBudget().doubleValue() : 0.0;

        return ClientProspectDTO.builder()
                .companyName(entity.getCompanyName())
                .estAnnualRevenue(revenue)
                .estAnnualRevenueFormatted(formatCompactCurrency(revenue))
                .yoyGrowthRate(growth)
                .yoyGrowthFormatted(formatGrowthPercent(growth))
                .growthDirection(growth > 0.01 ? "up" : growth < -0.01 ? "down" : "flat")
                .estAdBudget(budget)
                .estAdBudgetFormatted(formatCompactCurrency(budget))
                .industryVertical(entity.getIndustryVertical())
                .sectorFitScore(sectorFitScore)
                .build();
    }

    /**
     * Formats a USD amount into a compact representation:
     *   >= 1.0B  -> "$X.XB"
     *   >= 1.0M  -> "$X.XM"
     *   >= 1.0K  -> "$X.XK"
     *   otherwise -> "$X"
     */
    public static String formatCompactCurrency(double amount) {
        if (amount >= 1_000_000_000.0) {
            return "$" + String.format("%.1f", amount / 1_000_000_000.0) + "B";
        } else if (amount >= 1_000_000.0) {
            return "$" + String.format("%.1f", amount / 1_000_000.0) + "M";
        } else if (amount >= 1_000.0) {
            return "$" + String.format("%.1f", amount / 1_000.0) + "K";
        } else {
            return "$" + String.format("%.0f", amount);
        }
    }

    /**
     * Formats a growth rate percent:
     *   positive -> "+X.X%"
     *   negative -> "-X.X%"
     *   zero     -> "0.0%"
     */
    public static String formatGrowthPercent(double growthRate) {
        if (growthRate > 0.0) {
            return "+" + String.format("%.1f", growthRate) + "%";
        } else if (growthRate < 0.0) {
            return "-" + String.format("%.1f", Math.abs(growthRate)) + "%";
        } else {
            return "0.0%";
        }
    }
}
