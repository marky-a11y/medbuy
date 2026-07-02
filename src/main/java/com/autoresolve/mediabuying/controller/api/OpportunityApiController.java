package com.autoresolve.mediabuying.controller.api;

import com.autoresolve.mediabuying.model.dto.TopOpportunityDTO;
import com.autoresolve.mediabuying.service.CompositeScoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opportunity")
public class OpportunityApiController {

    private final CompositeScoringService compositeScoringService;

    public OpportunityApiController(CompositeScoringService compositeScoringService) {
        this.compositeScoringService = compositeScoringService;
    }

    @GetMapping("/top")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDIA_ANALYST', 'VIEWER')")
    public ResponseEntity<TopOpportunityDTO> getTopOpportunity() {
        TopOpportunityDTO top = compositeScoringService.calculateTopOpportunity();
        return ResponseEntity.ok(top);
    }
}
