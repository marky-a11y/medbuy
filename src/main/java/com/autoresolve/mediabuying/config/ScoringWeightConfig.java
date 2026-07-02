package com.autoresolve.mediabuying.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "scoring")
public class ScoringWeightConfig {

    private Map<String, Double> weights;
    private Targets targets;

    @Data
    public static class Targets {
        private Double roasTarget;
        private Double maxCac;
        private Double cltvTarget;
        private Double maxScalability;
        private Double conversionRateTarget;
    }
}
