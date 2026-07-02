package com.autoresolve.mediabuying.controller.api;

import com.autoresolve.mediabuying.model.dto.CalculatorInputDTO;
import com.autoresolve.mediabuying.model.dto.CalculatorResultDTO;
import com.autoresolve.mediabuying.service.CalculatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/calculator")
public class CalculatorApiController {

    private final CalculatorService calculatorService;

    public CalculatorApiController(CalculatorService calculatorService) {
        this.calculatorService = calculatorService;
    }

    @PostMapping("/compute")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDIA_ANALYST', 'VIEWER')")
    public ResponseEntity<CalculatorResultDTO> compute(
            @Valid @RequestBody CalculatorInputDTO input) {
        CalculatorResultDTO result = calculatorService.compute(input);
        return ResponseEntity.ok(result);
    }
}
