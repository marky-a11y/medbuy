package com.autoresolve.mediabuying.controller.admin;

import com.autoresolve.mediabuying.model.entity.ScoringWeight;
import com.autoresolve.mediabuying.repository.ScoringWeightRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ScoringWeightRepository scoringWeightRepository;

    public AdminController(ScoringWeightRepository scoringWeightRepository) {
        this.scoringWeightRepository = scoringWeightRepository;
    }

    @GetMapping
    public String adminPanel(Model model) {
        List<ScoringWeight> weights = scoringWeightRepository.findAll();
        model.addAttribute("weights", weights);
        return "admin";
    }

    @PostMapping("/weights/update")
    public String updateWeight(@RequestParam Long id,
                                @RequestParam BigDecimal weight) {
        ScoringWeight scoringWeight = scoringWeightRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid weight ID"));

        if (weight.compareTo(BigDecimal.ZERO) < 0 || weight.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Weight must be between 0.0 and 1.0");
        }

        scoringWeight.setWeight(weight);
        scoringWeightRepository.save(scoringWeight);

        return "redirect:/admin";
    }
}
