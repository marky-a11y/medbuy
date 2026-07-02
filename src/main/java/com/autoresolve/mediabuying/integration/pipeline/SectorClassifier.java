package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.messaging.dto.NormalizedSourceMessage;
import com.autoresolve.mediabuying.messaging.dto.SourceSectorMappingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 2 classifier that maps a {@link NormalizedSourceMessage} to one or more
 * business sectors by matching keywords in the normalized summary and raw data.
 * <p>
 * Configuration is externalised via {@code sector-classification.keyword-rules}
 * and {@code sector-classification.fallback-source-map} in {@code application.yml}.
 * </p>
 * <p>
 * When no keyword match is found, a fallback lookup by source name is attempted
 * (e.g., Skyscanner → {@code "travel"}, Indeed → {@code "job-market"}).
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "sector-classification")
public class SectorClassifier {

    private static final Logger log = LoggerFactory.getLogger(SectorClassifier.class);

    /** Sector name → list of keywords that trigger a match for that sector. */
    private Map<String, List<String>> keywordRules = new HashMap<>();

    /** Source name (lowercase) → fallback sector name when no keyword matches. */
    private Map<String, String> fallbackSourceMap = new HashMap<>();

    /**
     * Classify a single {@link NormalizedSourceMessage} against the configured
     * keyword rules.
     *
     * @param msg the normalised source message; may be {@code null}
     * @return a list of {@link SourceSectorMappingMessage} — one per matched sector,
     *         or an empty list if no sector matched and no fallback applied
     */
    public List<SourceSectorMappingMessage> classify(NormalizedSourceMessage msg) {
        if (msg == null) {
            log.warn("Received null NormalizedSourceMessage — returning empty list");
            return Collections.emptyList();
        }

        String textToMatch = buildTextToMatch(msg);
        if (textToMatch.trim().isEmpty() && hasNoFallback(msg.getSourceName())) {
            log.info("Empty text and no fallback for source '{}' — returning empty list", msg.getSourceName());
            return Collections.emptyList();
        }

        // Phase 1: keyword matching
        List<String> matchedSectors = new ArrayList<>();
        Map<String, Integer> matchCounts = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : keywordRules.entrySet()) {
            String sector = entry.getKey();
            List<String> keywords = entry.getValue();
            if (keywords == null || keywords.isEmpty()) {
                continue;
            }

            int matchCount = 0;
            for (String keyword : keywords) {
                if (textToMatch.contains(keyword.toLowerCase())) {
                    matchCount++;
                }
            }
            if (matchCount > 0) {
                matchedSectors.add(sector);
                matchCounts.put(sector, matchCount);
            }
        }

        // Phase 2: fallback by source name if no keyword matched
        if (matchedSectors.isEmpty()) {
            String fallbackSector = resolveFallback(msg.getSourceName());
            if (fallbackSector != null) {
                SourceSectorMappingMessage fallbackResult = new SourceSectorMappingMessage();
                fallbackResult.setSourceName(msg.getSourceName());
                fallbackResult.setMatchedSectors(Collections.singletonList(fallbackSector));
                fallbackResult.setClassificationMethod("SOURCE_NAME_FALLBACK");
                fallbackResult.setConfidenceScore(0.5);
                log.debug("Fallback classification for source '{}' → sector '{}'",
                        msg.getSourceName(), fallbackSector);
                return Collections.singletonList(fallbackResult);
            }

            log.info("No sector match or fallback for source '{}'", msg.getSourceName());
            return Collections.emptyList();
        }

        // Phase 3: build per-sector results with confidence scores
        List<SourceSectorMappingMessage> results = new ArrayList<>();
        for (String sector : matchedSectors) {
            List<String> keywords = keywordRules.get(sector);
            int matchCount = matchCounts.getOrDefault(sector, 0);
            double confidence = computeConfidence(matchCount, keywords != null ? keywords.size() : 0);

            SourceSectorMappingMessage result = new SourceSectorMappingMessage();
            result.setSourceName(msg.getSourceName());
            result.setMatchedSectors(Collections.singletonList(sector));
            result.setClassificationMethod("KEYWORD_MATCH");
            result.setConfidenceScore(confidence);
            results.add(result);
        }

        log.debug("Classified source '{}' into {} sector(s): {}",
                msg.getSourceName(), results.size(), matchedSectors);
        return results;
    }

    // ---- internal helpers ----

    /**
     * Build a single lower-cased string from the message fields that should be
     * searched for keyword matches.
     */
    String buildTextToMatch(NormalizedSourceMessage msg) {
        StringBuilder sb = new StringBuilder(256);
        if (msg.getNormalizedSummary() != null) {
            sb.append(msg.getNormalizedSummary().toLowerCase());
        }
        sb.append(' ');
        if (msg.getRawData() != null) {
            sb.append(msg.getRawData().toLowerCase());
        }
        sb.append(' ');
        // Also index the source name for partial matches
        if (msg.getSourceName() != null) {
            sb.append(msg.getSourceName().toLowerCase());
        }
        return sb.toString();
    }

    private String resolveFallback(String sourceName) {
        if (sourceName == null || fallbackSourceMap == null) {
            return null;
        }
        return fallbackSourceMap.get(sourceName.toLowerCase().trim());
    }

    private boolean hasNoFallback(String sourceName) {
        return resolveFallback(sourceName) == null;
    }

    /**
     * Compute a confidence score in the range [0.0, 1.0] based on the ratio of
     * matched keywords to total keywords for a given sector.
     */
    static double computeConfidence(int matchCount, int totalKeywords) {
        if (totalKeywords == 0) {
            return 0.0;
        }
        double raw = (double) matchCount / (double) totalKeywords;
        return Math.min(1.0, Math.max(0.0, raw));
    }

    // ---- configuration property accessors ----

    public Map<String, List<String>> getKeywordRules() {
        return keywordRules;
    }

    public void setKeywordRules(Map<String, List<String>> keywordRules) {
        this.keywordRules = keywordRules;
    }

    public Map<String, String> getFallbackSourceMap() {
        return fallbackSourceMap;
    }

    public void setFallbackSourceMap(Map<String, String> fallbackSourceMap) {
        this.fallbackSourceMap = fallbackSourceMap;
    }
}
