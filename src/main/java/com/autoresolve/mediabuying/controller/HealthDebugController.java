package com.autoresolve.mediabuying.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple health/debug endpoint for Railway (and other PaaS) health checks.
 * Returns a lightweight "OK" response that bypasses all view resolution,
 * JSF/Thymeleaf conflicts, and security filters.
 */
@RestController
public class HealthDebugController {

    @GetMapping("/")
    public String home() {
        return "OK";
    }
}
