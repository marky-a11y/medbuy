package com.autoresolve.mediabuying.controller;

import com.autoresolve.mediabuying.controller.api.CalculatorApiController;
import com.autoresolve.mediabuying.model.dto.CalculatorInputDTO;
import com.autoresolve.mediabuying.model.dto.CalculatorResultDTO;
import com.autoresolve.mediabuying.service.CalculatorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalculatorControllerTest {

    @Mock
    private CalculatorService calculatorService;

    @Test
    void testComputeReturnsResult() {
        CalculatorApiController controller = new CalculatorApiController(calculatorService);

        CalculatorInputDTO input = new CalculatorInputDTO();
        input.setMonthlyAdSpend(1000.0);
        input.setCostPerClick(5.0);
        input.setClickThroughRate(0.05);
        input.setClickToLeadRate(0.12);
        input.setLeadToCustomerRate(0.30);
        input.setMonthlyRevenuePerCustomer(80.0);
        input.setAverageRetentionMonths(8);
        input.setGrossMargin(0.70);

        CalculatorResultDTO expectedResult = CalculatorResultDTO.builder()
                .roas(3.2)
                .cac(143.0)
                .ltv(448.0)
                .confidence("Medium")
                .build();

        when(calculatorService.compute(any(CalculatorInputDTO.class))).thenReturn(expectedResult);

        ResponseEntity<CalculatorResultDTO> response = controller.compute(input);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        CalculatorResultDTO result = response.getBody();
        assertNotNull(result);
        assertEquals(3.2, result.getRoas(), 0.01);
        assertEquals("Medium", result.getConfidence());
    }
}
