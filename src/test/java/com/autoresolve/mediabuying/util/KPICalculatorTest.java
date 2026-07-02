package com.autoresolve.mediabuying.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class KPICalculatorTest {

    @Test
    void testCalculateRoas() {
        BigDecimal revenue = BigDecimal.valueOf(10000);
        BigDecimal adSpend = BigDecimal.valueOf(2000);

        BigDecimal roas = KPICalculator.calculateRoas(revenue, adSpend);

        assertEquals(BigDecimal.valueOf(5.00), roas);
    }

    @Test
    void testCalculateRoasWithZeroSpend() {
        BigDecimal revenue = BigDecimal.valueOf(10000);
        BigDecimal adSpend = BigDecimal.ZERO;

        BigDecimal roas = KPICalculator.calculateRoas(revenue, adSpend);

        assertEquals(BigDecimal.ZERO, roas);
    }

    @Test
    void testCalculateCac() {
        BigDecimal totalCost = BigDecimal.valueOf(50000);
        Long newCustomers = 100L;

        BigDecimal cac = KPICalculator.calculateCac(totalCost, newCustomers);

        assertEquals(BigDecimal.valueOf(500.00), cac);
    }

    @Test
    void testCalculateCacWithZeroCustomers() {
        BigDecimal totalCost = BigDecimal.valueOf(50000);
        Long newCustomers = 0L;

        BigDecimal cac = KPICalculator.calculateCac(totalCost, newCustomers);

        assertEquals(BigDecimal.ZERO, cac);
    }

    @Test
    void testCalculateConversionRate() {
        Long conversions = 50L;
        Long totalInteractions = 1000L;

        BigDecimal cr = KPICalculator.calculateConversionRate(conversions, totalInteractions);

        assertEquals(BigDecimal.valueOf(0.0500), cr);
    }

    @Test
    void testCalculateConversionRateWithZeroInteractions() {
        Long conversions = 50L;
        Long totalInteractions = 0L;

        BigDecimal cr = KPICalculator.calculateConversionRate(conversions, totalInteractions);

        assertEquals(BigDecimal.ZERO, cr);
    }

    @Test
    void testNormalizeValueWithinTarget() {
        double result = KPICalculator.normalize(3.0, 5.0);
        assertEquals(0.6, result, 0.001);
    }

    @Test
    void testNormalizeValueExceedsTarget() {
        double result = KPICalculator.normalize(10.0, 5.0);
        assertEquals(1.0, result, 0.001);
    }

    @Test
    void testNormalizeWithZeroTarget() {
        double result = KPICalculator.normalize(10.0, 0.0);
        assertEquals(0.0, result);
    }
}
