package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.messaging.dto.NormalizedSourceMessage;
import com.autoresolve.mediabuying.messaging.dto.SourceSectorMappingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SectorClassifier}.
 * <p>
 * Verifies keyword matching (exact, partial, case-insensitive), multiple sector
 * matches, no-match fallback, confidence score computation, and edge cases
 * (null/empty input).
 * </p>
 */
class SectorClassifierTest {

    private SectorClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new SectorClassifier();

        Map<String, List<String>> rules = new HashMap<>();
        rules.put("technology", Arrays.asList("software", "saas", "cloud", "ai", "tech", "digital", "platform", "app", "startup"));
        rules.put("finance", Arrays.asList("bank", "finance", "insurance", "investment", "trading", "fintech", "payment", "lending"));
        rules.put("manufacturing", Arrays.asList("manufacturing", "industrial", "factory", "production", "supply-chain", "logistics"));
        rules.put("retail", Arrays.asList("retail", "ecommerce", "shop", "store", "marketplace", "consumer-goods", "fashion"));
        rules.put("health-wellness", Arrays.asList("health", "wellness", "fitness", "medical", "pharma", "healthcare", "nutrition"));
        rules.put("travel", Arrays.asList("travel", "hotel", "flight", "tourism", "hospitality", "airline", "booking"));
        rules.put("job-market", Arrays.asList("job", "hiring", "career", "employment", "recruitment", "staffing", "hr"));
        classifier.setKeywordRules(rules);

        Map<String, String> fallbackMap = new HashMap<>();
        fallbackMap.put("pytrends", "technology");
        fallbackMap.put("skyscanner", "travel");
        fallbackMap.put("ebay", "retail");
        fallbackMap.put("job-market", "job-market");
        classifier.setFallbackSourceMap(fallbackMap);
    }

    // ---------------------------------------------------------------
    // 1. Exact keyword match
    // ---------------------------------------------------------------
    @Test
    void testExactKeywordMatchReturnsCorrectSector() {
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setSourceName("test_source");
        msg.setNormalizedSummary("This is a software company that builds cloud platforms");

        List<SourceSectorMappingMessage> results = classifier.classify(msg);

        assertNotNull(results);
        assertEquals(1, results.size(), "Should match exactly one sector");
        assertEquals("technology", results.get(0).getMatchedSectors().get(0));
        assertEquals("KEYWORD_MATCH", results.get(0).getClassificationMethod());
        assertTrue(results.get(0).getConfidenceScore() > 0.0,
                "Confidence should be greater than 0 for a match");
    }

    // ---------------------------------------------------------------
    // 2. Partial / substring keyword match
    // ---------------------------------------------------------------
    @Test
    void testPartialKeywordMatch() {
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setSourceName("startup_inc");
        msg.setNormalizedSummary("Our digital transformation platform leverages AI for logistics");

        List<SourceSectorMappingMessage> results = classifier.classify(msg);

        assertNotNull(results);
        // "digital" → technology, "platform" → technology, "ai" → technology, "logistics" → manufacturing
        // Should match at least technology
        assertTrue(results.size() >= 1, "Should match at least the technology sector");

        boolean foundTech = results.stream()
                .anyMatch(r -> r.getMatchedSectors().get(0).equals("technology"));
        assertTrue(foundTech, "Should match technology sector (digital, platform, ai)");
    }

    // ---------------------------------------------------------------
    // 3. Case-insensitive matching
    // ---------------------------------------------------------------
    @Test
    void testCaseInsensitiveMatching() {
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setSourceName("MiXeD_CaSe");
        msg.setNormalizedSummary("SAAS Platform with AI-Powered Analytics and CLOUD infrastructure");

        List<SourceSectorMappingMessage> results = classifier.classify(msg);

        assertNotNull(results);
        assertFalse(results.isEmpty(), "Should match despite mixed case in input");
        // "saas", "platform", "ai", "cloud" should all match technology
        assertTrue(results.stream().anyMatch(r -> "technology".equals(r.getMatchedSectors().get(0))),
                "Should match technology (saas, platform, ai, cloud)");
    }

    // ---------------------------------------------------------------
    // 4. Multiple sector matches
    // ---------------------------------------------------------------
    @Test
    void testMultipleSectorMatches() {
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setSourceName("fintech_retail");
        msg.setNormalizedSummary("We offer a fintech payment platform for retail ecommerce stores");

        List<SourceSectorMappingMessage> results = classifier.classify(msg);

        assertNotNull(results);
        assertTrue(results.size() >= 2, "Should match at least two sectors");

        List<String> matchedSectorNames = new java.util.ArrayList<>();
        for (SourceSectorMappingMessage r : results) {
            matchedSectorNames.add(r.getMatchedSectors().get(0));
        }

        assertTrue(matchedSectorNames.contains("technology"),
                "'platform' and 'fintech' should match technology");
        assertTrue(matchedSectorNames.contains("finance"),
                "'fintech' and 'payment' should match finance");
        assertTrue(matchedSectorNames.contains("retail"),
                "'retail' and 'ecommerce' and 'store' should match retail");
    }

    // ---------------------------------------------------------------
    // 5. No-match fallback to source name
    // ---------------------------------------------------------------
    @Test
    void testNoMatchFallbackBySourceName() {
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setSourceName("skyscanner");
        // Purposely keyword-free text — should trigger Skyscanner → travel fallback
        msg.setNormalizedSummary("Weekly aggregated price index across European markets");
        msg.setRawData("{\"origin\":\"CDG\",\"destination\":\"FRA\"}");

        List<SourceSectorMappingMessage> results = classifier.classify(msg);

        assertNotNull(results);
        assertEquals(1, results.size(), "Fallback should produce exactly one result");
        assertEquals("travel", results.get(0).getMatchedSectors().get(0));
        assertEquals("SOURCE_NAME_FALLBACK", results.get(0).getClassificationMethod());
        assertEquals(0.5, results.get(0).getConfidenceScore(), 0.001,
                "Fallback confidence should be 0.5");
    }

    // ---------------------------------------------------------------
    // 6. Confidence score computation
    // ---------------------------------------------------------------
    @Test
    void testConfidenceScoreComputation() {
        // Technology has 9 keywords: software, saas, cloud, ai, it-services,
        // tech, digital, platform, app, startup
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setSourceName("confidence_test");
        // Match exactly 3 keywords: "software", "cloud", "ai"
        msg.setNormalizedSummary("Our software runs on the cloud using AI models");

        List<SourceSectorMappingMessage> results = classifier.classify(msg);

        assertNotNull(results);
        assertFalse(results.isEmpty(), "Should have at least one match");

        SourceSectorMappingMessage techResult = results.stream()
                .filter(r -> "technology".equals(r.getMatchedSectors().get(0)))
                .findFirst()
                .orElse(null);

        assertNotNull(techResult, "Should have matched technology");

        // technology has 10 keywords (software, saas, cloud, ai, it-services,
        // tech, digital, platform, app, startup)
        // Matched: software, cloud, ai = 3 matches out of 10 = 0.3
        int totalKeywords = classifier.getKeywordRules().get("technology").size();
        double expectedConfidence = 3.0 / totalKeywords;
        assertEquals(expectedConfidence, techResult.getConfidenceScore(), 0.001,
                "Confidence should be matchCount / totalKeywords for the sector");
    }

    // ---------------------------------------------------------------
    // 7. Empty input (null/empty summary and raw data, no fallback)
    // ---------------------------------------------------------------
    @Test
    void testEmptyInputReturnsEmptyList() {
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setSourceName("unknown_source");
        msg.setNormalizedSummary(null);
        msg.setRawData(null);

        List<SourceSectorMappingMessage> results = classifier.classify(msg);

        assertNotNull(results, "Should never return null");
        assertTrue(results.isEmpty(), "Empty input with no fallback should return empty list");
    }

    // ---------------------------------------------------------------
    // 8. Null message returns empty list
    // ---------------------------------------------------------------
    @Test
    void testNullMessageReturnsEmptyList() {
        List<SourceSectorMappingMessage> results = classifier.classify(null);
        assertNotNull(results, "Null message should return empty list, not null");
        assertTrue(results.isEmpty(), "Null message should return empty list");
    }

    // ---------------------------------------------------------------
    // 9. Raw data also contributes to matching (not just summary)
    // ---------------------------------------------------------------
    @Test
    void testRawDataIsAlsoSearched() {
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setSourceName("raw_match_test");
        msg.setNormalizedSummary("General market report");
        // Keywords only in rawData
        msg.setRawData("{\"industry\":\"manufacturing\",\"sector\":\"industrial automation\"}");

        List<SourceSectorMappingMessage> results = classifier.classify(msg);

        assertNotNull(results);
        assertTrue(results.stream().anyMatch(r -> "manufacturing".equals(r.getMatchedSectors().get(0))),
                "'manufacturing' and 'industrial' in rawData should match manufacturing sector");
    }

    // ---------------------------------------------------------------
    // 10. Confidence of 0.0 when no keywords match (before fallback check)
    // ---------------------------------------------------------------
    @Test
    void testNoMatchAndNoFallbackReturnsEmpty() {
        // Source not in fallback map and no keywords in text
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setSourceName("completely_unknown_source");
        msg.setNormalizedSummary("Nothing here matches any keyword");

        List<SourceSectorMappingMessage> results = classifier.classify(msg);

        assertNotNull(results);
        assertTrue(results.isEmpty(), "No match and no fallback should produce empty list");
    }
}
