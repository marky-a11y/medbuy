package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.dto.CalculatorInputDTO;
import com.autoresolve.mediabuying.model.dto.CalculatorResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorServiceTest {

    private CalculatorService calculatorService;

    @BeforeEach
    void setUp() {
        calculatorService = new CalculatorService();
    }

    @Test
    void testComputeReturnsValidResult() {
        CalculatorInputDTO input = new CalculatorInputDTO();
        input.setMonthlyAdSpend(1000.0);
        input.setCostPerClick(5.0);
        input.setClickThroughRate(0.05);
        input.setClickToLeadRate(0.12);
        input.setLeadToCustomerRate(0.30);
        input.setMonthlyRevenuePerCustomer(80.0);
        input.setAverageRetentionMonths(8);
        input.setGrossMargin(0.70);
        input.setBusinessType("gym");

        CalculatorResultDTO result = calculatorService.compute(input);

        assertNotNull(result);
        assertEquals(200, result.getClicks());
        assertEquals(24, result.getLeads());
        assertEquals(7, result.getCustomers());
        assertEquals(448.0, result.getLtv(), 0.01);
        assertTrue(result.getRoas() > 0);
        assertTrue(result.getCac() > 0);
        assertTrue(result.getLtvToCac() > 0);
        assertNotNull(result.getConfidence());
    }

    @Test
    void testZeroSpendReturnsZeroRoi() {
        CalculatorInputDTO input = new CalculatorInputDTO();
        input.setMonthlyAdSpend(0.0);
        input.setCostPerClick(5.0);
        input.setClickThroughRate(0.05);
        input.setClickToLeadRate(0.12);
        input.setLeadToCustomerRate(0.30);
        input.setMonthlyRevenuePerCustomer(80.0);
        input.setAverageRetentionMonths(8);
        input.setGrossMargin(0.70);

        CalculatorResultDTO result = calculatorService.compute(input);
        assertEquals(0, result.getRoas());
    }

    @Test
    void testLowConfidenceWithFewInputs() {
        CalculatorInputDTO input = new CalculatorInputDTO();
        input.setMonthlyAdSpend(1000.0);
        input.setCostPerClick(5.0);
        input.setClickThroughRate(0.05);
        input.setClickToLeadRate(0.12);
        input.setLeadToCustomerRate(0.30);
        input.setMonthlyRevenuePerCustomer(80.0);
        input.setAverageRetentionMonths(8);
        input.setGrossMargin(0.70);

        CalculatorResultDTO result = calculatorService.compute(input);
        assertTrue("Low".equals(result.getConfidence()) || "Medium".equals(result.getConfidence()));
    }

    @Test
    void testSensitivityAnalysis() {
        CalculatorInputDTO input = new CalculatorInputDTO();
        input.setMonthlyAdSpend(1000.0);
        input.setCostPerClick(5.0);
        input.setClickThroughRate(0.05);
        input.setClickToLeadRate(0.12);
        input.setLeadToCustomerRate(0.30);
        input.setMonthlyRevenuePerCustomer(80.0);
        input.setAverageRetentionMonths(8);
        input.setGrossMargin(0.70);

        CalculatorResultDTO result = calculatorService.compute(input);
        assertTrue(result.getLowCaseRoi() <= result.getHighCaseRoi(),
                "Low case ROI should not exceed high case ROI");
    }

    @Test
    void testBusinessTypeLabel() {
        CalculatorInputDTO input = new CalculatorInputDTO();
        input.setMonthlyAdSpend(1000.0);
        input.setCostPerClick(5.0);
        input.setClickThroughRate(0.05);
        input.setClickToLeadRate(0.12);
        input.setLeadToCustomerRate(0.30);
        input.setMonthlyRevenuePerCustomer(80.0);
        input.setAverageRetentionMonths(8);
        input.setGrossMargin(0.70);
        input.setBusinessType("personal_training");

        CalculatorResultDTO result = calculatorService.compute(input);
        assertEquals("Personal Training", result.getBusinessTypeLabel());
    }
}
