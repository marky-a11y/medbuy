package com.autoresolve.mediabuying.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility class for KPI mathematical calculations.
 */
public final class KPICalculator {

    private KPICalculator() {
    }

    /**
     * Calculates Return on Ad Spend (ROAS).
     * ROAS = Revenue / Ad Spend
     */
    public static BigDecimal calculateRoas(BigDecimal revenue, BigDecimal adSpend) {
        if (adSpend == null || BigDecimal.ZERO.compareTo(adSpend) == 0) {
            return BigDecimal.ZERO;
        }
        return revenue.divide(adSpend, 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates Customer Acquisition Cost (CAC).
     * CAC = Total Acquisition Cost / Number of New Customers
     */
    public static BigDecimal calculateCac(BigDecimal totalCost, Long newCustomers) {
        if (newCustomers == null || newCustomers == 0) {
            return BigDecimal.ZERO;
        }
        return totalCost.divide(BigDecimal.valueOf(newCustomers), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates Conversion Rate.
     * CR = Conversions / Total Interactions
     */
    public static BigDecimal calculateConversionRate(Long conversions, Long totalInteractions) {
        if (totalInteractions == null || totalInteractions == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(conversions)
                .divide(BigDecimal.valueOf(totalInteractions), 4, RoundingMode.HALF_UP);
    }

    /**
     * Calculates Payback Period in months.
     * Payback Period = CAC / (Average Monthly Revenue per Customer)
     */
    public static BigDecimal calculatePaybackPeriod(BigDecimal cac, BigDecimal monthlyRevenue) {
        if (monthlyRevenue == null || BigDecimal.ZERO.compareTo(monthlyRevenue) == 0) {
            return BigDecimal.ZERO;
        }
        return cac.divide(monthlyRevenue, 2, RoundingMode.HALF_UP);
    }

    /**
     * Normalizes a value against a target, capped at 1.0.
     */
    public static double normalize(double value, double target) {
        if (target <= 0) return 0;
        return Math.min(value / target, 1.0);
    }
}
