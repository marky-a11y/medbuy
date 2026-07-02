package com.autoresolve.mediabuying.controller.calculator;

import com.autoresolve.mediabuying.model.dto.CalculatorInputDTO;
import com.autoresolve.mediabuying.model.dto.CalculatorResultDTO;
import com.autoresolve.mediabuying.model.dto.MarketEstimatesDTO;
import com.autoresolve.mediabuying.model.dto.PlatformDTO;
import com.autoresolve.mediabuying.model.dto.SectorDTO;
import com.autoresolve.mediabuying.service.CalculatorService;
import com.autoresolve.mediabuying.service.MarketDataService;
import com.autoresolve.mediabuying.service.PlatformSectorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class CalculatorController {

    private final CalculatorService calculatorService;
    private final PlatformSectorService platformSectorService;
    private final MarketDataService marketDataService;

    public CalculatorController(CalculatorService calculatorService,
                                 PlatformSectorService platformSectorService,
                                 MarketDataService marketDataService) {
        this.calculatorService = calculatorService;
        this.platformSectorService = platformSectorService;
        this.marketDataService = marketDataService;
    }

    @GetMapping("/calculator")
    public String showCalculator(Model model,
                                  @RequestParam(value = "platform", required = false) String platformName,
                                  @RequestParam(value = "sector", required = false) String sectorName,
                                  @RequestParam(value = "businessType", required = false) String businessType,
                                  @RequestParam(value = "city", required = false) String city,
                                  @RequestParam(value = "platformId", required = false) Long platformId,
                                  @RequestParam(value = "sectorId", required = false) Long sectorId) {
        addCommonModelAttributes(model);

        CalculatorInputDTO input = new CalculatorInputDTO();

        // Support both name-based and ID-based params (ID-based from dashboard Top Pick link).
        // Also fallback to IDs when name-based params are empty strings (e.g. dropdown
        // default "Select..." option submitted with value="").
        if ((platformName == null || platformName.isEmpty()) && platformId != null) {
            com.autoresolve.mediabuying.model.entity.Platform p = platformSectorService.getPlatformById(platformId);
            if (p != null) platformName = p.getName();
        }
        if ((sectorName == null || sectorName.isEmpty()) && sectorId != null) {
            com.autoresolve.mediabuying.model.entity.CommerceSector s = platformSectorService.getSectorById(sectorId);
            if (s != null) sectorName = s.getName();
        }

        // If all dropdowns selected, pre-populate with market estimates
        if (platformName != null && !platformName.isEmpty()
                && sectorName != null && !sectorName.isEmpty()
                && businessType != null && !businessType.isEmpty()
                && city != null && !city.isEmpty()) {
            MarketEstimatesDTO est = marketDataService.getEstimates(platformName, sectorName, businessType, city);
            input.setMonthlyAdSpend(1000.0); // Default budget
            input.setCostPerClick(est.getEstimatedCpc());
            // MarketDataService returns decimals; convert to percentages for the form
            input.setClickThroughRate(est.getEstimatedCtr() != null ? est.getEstimatedCtr() * 100.0 : null);
            input.setClickToLeadRate(est.getEstimatedClickToLeadRate() != null ? est.getEstimatedClickToLeadRate() * 100.0 : null);
            input.setLeadToCustomerRate(est.getEstimatedLeadToCustomerRate() != null ? est.getEstimatedLeadToCustomerRate() * 100.0 : null);
            input.setMonthlyRevenuePerCustomer(est.getEstimatedMonthlyRevenuePerCustomer());
            input.setAverageRetentionMonths(est.getEstimatedRetentionMonths());
            input.setGrossMargin(est.getEstimatedGrossMargin() != null ? est.getEstimatedGrossMargin() * 100.0 : null);
            input.setBusinessType(businessType);
            model.addAttribute("estimates", est);
        }

        // Pass selections back to template for dropdown pre-selection
        model.addAttribute("calculatorInput", input);
        model.addAttribute("selectedPlatform", platformName);
        model.addAttribute("selectedSector", sectorName);
        model.addAttribute("selectedBusinessType", businessType);
        model.addAttribute("selectedCity", city);

        return "calculator";
    }

    @PostMapping("/calculator/compute")
    public String compute(@ModelAttribute CalculatorInputDTO input, Model model) {
        // Convert percentage inputs to decimal values for the service
        if (input.getClickThroughRate() != null) {
            input.setClickThroughRate(input.getClickThroughRate() / 100.0);
        }
        if (input.getClickToLeadRate() != null) {
            input.setClickToLeadRate(input.getClickToLeadRate() / 100.0);
        }
        if (input.getLeadToCustomerRate() != null) {
            input.setLeadToCustomerRate(input.getLeadToCustomerRate() / 100.0);
        }
        if (input.getGrossMargin() != null) {
            input.setGrossMargin(input.getGrossMargin() / 100.0);
        }
        CalculatorResultDTO result = calculatorService.compute(input);
        // Convert results back to percentage for display
        if (result != null) {
            result.setClickToLeadRate(result.getClickToLeadRate() * 100.0);
            result.setLeadToCustomerRate(result.getLeadToCustomerRate() * 100.0);
            result.setGrossMargin(result.getGrossMargin() * 100.0);
        }
        model.addAttribute("result", result);
        model.addAttribute("calculatorInput", input);
        addCommonModelAttributes(model);
        return "calculator";
    }

    /**
     * Convert pre-populated market estimate decimals to percentage values for the form.
     */
    private void convertEstimatesToPercentage(CalculatorInputDTO input) {
        if (input.getClickThroughRate() != null) {
            input.setClickThroughRate(input.getClickThroughRate() * 100.0);
        }
        if (input.getClickToLeadRate() != null) {
            input.setClickToLeadRate(input.getClickToLeadRate() * 100.0);
        }
        if (input.getLeadToCustomerRate() != null) {
            input.setLeadToCustomerRate(input.getLeadToCustomerRate() * 100.0);
        }
        if (input.getGrossMargin() != null) {
            input.setGrossMargin(input.getGrossMargin() * 100.0);
        }
    }

    private void addCommonModelAttributes(Model model) {
        model.addAttribute("platforms", platformSectorService.getActivePlatforms());
        model.addAttribute("sectors", platformSectorService.getAllSectors().stream()
                .map(s -> SectorDTO.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .displayName(s.getDisplayName())
                        .isActive(s.getIsActive())
                        .build())
                .collect(Collectors.toList()));
        model.addAttribute("businessTypes", getBusinessTypes());
        model.addAttribute("usCities", marketDataService.getUsCities());
    }

    /**
     * Business types grouped under the Health & Wellness sector.
     */
    private List<BusinessTypeOption> getBusinessTypes() {
        return Arrays.asList(
                new BusinessTypeOption("gym", "Gym / Fitness Center"),
                new BusinessTypeOption("personal_training", "Personal Training"),
                new BusinessTypeOption("yoga", "Yoga / Pilates Studio"),
                new BusinessTypeOption("crossfit", "CrossFit Box"),
                new BusinessTypeOption("other", "Other")
        );
    }

    /**
     * Simple DTO for business type dropdown options.
     */
    public static class BusinessTypeOption {
        private final String value;
        private final String label;
        public BusinessTypeOption(String value, String label) { this.value = value; this.label = label; }
        public String getValue() { return value; }
        public String getLabel() { return label; }
    }
}
