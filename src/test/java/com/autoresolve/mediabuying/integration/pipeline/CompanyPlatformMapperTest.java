package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.messaging.dto.CompanyPlatformMappingMessage;
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
 * Unit tests for {@link CompanyPlatformMapper}.
 * <p>
 * Verifies company extraction from each source type, platform inference,
 * confidence scoring, and edge cases.
 * </p>
 */
class CompanyPlatformMapperTest {

    private CompanyPlatformMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CompanyPlatformMapper();

        // Configure extraction patterns
        Map<String, String> extractionPatterns = new HashMap<>();
        extractionPatterns.put("ebay", "seller:(\\w+)");
        extractionPatterns.put("yelp", "business:(.+)");
        extractionPatterns.put("reddit", "brand:(\\w+)");
        extractionPatterns.put("pytrends", "keyword-as-company");
        extractionPatterns.put("default", "source-name");
        mapper.setSourceExtractionPatterns(extractionPatterns);

        // Configure business type → platform mappings
        Map<String, List<String>> platformMappings = new HashMap<>();
        platformMappings.put("local-business", Arrays.asList("yelp_ads", "foursquare_ads"));
        platformMappings.put("e-commerce", Arrays.asList("google_shopping", "meta_ads"));
        platformMappings.put("b2b-saas", Collections.singletonList("linkedin_ads"));
        platformMappings.put("travel", Arrays.asList("bing_ads", "skyscanner_ads"));
        platformMappings.put("job-market", Arrays.asList("linkedin_ads", "google_ads"));
        mapper.setBusinessTypeToPlatform(platformMappings);

        // Configure default platform per sector
        Map<String, String> defaultPlatforms = new HashMap<>();
        defaultPlatforms.put("technology", "linkedin_ads");
        defaultPlatforms.put("finance", "linkedin_ads");
        defaultPlatforms.put("manufacturing", "google_ads");
        defaultPlatforms.put("retail", "meta_ads");
        defaultPlatforms.put("health-wellness", "google_ads");
        defaultPlatforms.put("travel", "bing_ads");
        defaultPlatforms.put("job-market", "linkedin_ads");
        mapper.setDefaultPlatformPerSector(defaultPlatforms);
    }

    // ---------------------------------------------------------------
    // 1. eBay extraction: extract seller names from text
    // ---------------------------------------------------------------
    @Test
    void testExtractEbaySellers() {
        List<String> companies = mapper.extractCompanies("ebay", "seller:TechGadgets, great condition");
        assertNotNull(companies);
        assertEquals(1, companies.size());
        assertEquals("TechGadgets", companies.get(0));
    }

    // ---------------------------------------------------------------
    // 2. Yelp extraction: extract business name from text
    // ---------------------------------------------------------------
    @Test
    void testExtractYelpBusiness() {
        List<String> companies = mapper.extractCompanies("yelp", "business:Sunset Cafe");
        assertNotNull(companies);
        assertEquals(1, companies.size());
        assertTrue(companies.get(0).contains("Sunset Cafe"));
    }

    // ---------------------------------------------------------------
    // 3. Reddit extraction: extract brand names from text
    // ---------------------------------------------------------------
    @Test
    void testExtractRedditBrands() {
        // Reddit extraction looks for brand keywords in text (not regex)
        List<String> companies = mapper.extractCompanies("reddit", "brand:Tesla is mentioned often");
        assertNotNull(companies);
        assertFalse(companies.isEmpty());
        assertTrue(companies.contains("Tesla"));
    }

    // ---------------------------------------------------------------
    // 4. Pytrends: use first word as company proxy
    // ---------------------------------------------------------------
    @Test
    void testExtractPytrendsKeyword() {
        List<String> companies = mapper.extractCompanies("pytrends", "Artificial Intelligence search trends");
        assertNotNull(companies);
        assertEquals(1, companies.size());
        assertEquals("Artificial", companies.get(0));
    }

    // ---------------------------------------------------------------
    // 5. Default source: use source name as proxy
    // ---------------------------------------------------------------
    @Test
    void testExtractDefaultSource() {
        List<String> companies = mapper.extractCompanies("unknown_source", "Some random text");
        assertNotNull(companies);
        assertEquals(1, companies.size());
        assertEquals("unknown_source", companies.get(0));
    }

    // ---------------------------------------------------------------
    // 6. Platform inference — e-commerce sector
    // ---------------------------------------------------------------
    @Test
    void testInferPlatformsECommerce() {
        String businessType = mapper.inferBusinessType("retail");
        assertEquals("e-commerce", businessType);

        List<String> platforms = mapper.inferPlatforms(businessType, "retail");
        assertNotNull(platforms);
        assertTrue(platforms.contains("google_shopping"));
        assertTrue(platforms.contains("meta_ads"));
    }

    // ---------------------------------------------------------------
    // 7. Platform inference — local business
    // ---------------------------------------------------------------
    @Test
    void testInferPlatformsLocalBusiness() {
        String businessType = mapper.inferBusinessType("health-wellness");
        assertEquals("local-business", businessType);

        List<String> platforms = mapper.inferPlatforms(businessType, "health-wellness");
        assertNotNull(platforms);
        assertTrue(platforms.contains("yelp_ads"));
        assertTrue(platforms.contains("foursquare_ads"));
    }

    // ---------------------------------------------------------------
    // 8. Platform inference — B2B SaaS (technology)
    // ---------------------------------------------------------------
    @Test
    void testInferPlatformsB2BSaas() {
        String businessType = mapper.inferBusinessType("technology");
        assertEquals("b2b-saas", businessType);

        List<String> platforms = mapper.inferPlatforms(businessType, "technology");
        assertNotNull(platforms);
        assertTrue(platforms.contains("linkedin_ads"));
        assertEquals(1, platforms.size());
    }

    // ---------------------------------------------------------------
    // 9. Default platform via sector fallback
    // ---------------------------------------------------------------
    @Test
    void testInferPlatformsFallsBackToDefaultPerSector() {
        // manufacturing is mapped to b2b-saas business type
        List<String> platforms = mapper.inferPlatforms("b2b-saas", "manufacturing");
        // Business type mapping takes precedence: b2b-saas → linkedin_ads
        assertNotNull(platforms);
        assertEquals(1, platforms.size());
        assertTrue(platforms.contains("linkedin_ads"));
    }

    // ---------------------------------------------------------------
    // 10. Confidence scoring
    // ---------------------------------------------------------------
    @Test
    void testConfidenceScoring() {
        double ecomConfidence = mapper.computeConfidence("e-commerce", 2);
        assertEquals(0.85, ecomConfidence, 0.001);

        double travelConfidence = mapper.computeConfidence("travel", 2);
        assertEquals(0.80, travelConfidence, 0.001);

        double defaultConfidence = mapper.computeConfidence("default", 1);
        assertEquals(0.50, defaultConfidence, 0.001);

        double nullConfidence = mapper.computeConfidence(null, 0);
        assertEquals(0.30, nullConfidence, 0.001);
    }

    // ---------------------------------------------------------------
    // 11. map() with null message → empty list
    // ---------------------------------------------------------------
    @Test
    void testMapNullMessageReturnsEmptyList() {
        List<CompanyPlatformMappingMessage> results = mapper.map(null);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ---------------------------------------------------------------
    // 12. map() with known source yields correct mapping
    // ---------------------------------------------------------------
    @Test
    void testMapWithKnownSource() {
        SourceSectorMappingMessage msg = new SourceSectorMappingMessage();
        msg.setSourceName("ebay");
        msg.setMatchedSectors(Collections.singletonList("retail"));

        List<CompanyPlatformMappingMessage> results = mapper.map(msg);
        assertNotNull(results);
        assertFalse(results.isEmpty());

        CompanyPlatformMappingMessage mapping = results.get(0);
        assertNotNull(mapping.getEventId());
        assertEquals("retail", mapping.getSectorName());
        assertEquals("HEURISTIC", mapping.getMappingMethod());
        assertNotNull(mapping.getInferredAdPlatforms());
        assertTrue(mapping.getConfidenceScore() > 0.0);
    }

    // ---------------------------------------------------------------
    // 13. map() with travel source infers travel platforms
    // ---------------------------------------------------------------
    @Test
    void testMapTravelSourceInfersTravelPlatforms() {
        SourceSectorMappingMessage msg = new SourceSectorMappingMessage();
        msg.setSourceName("skyscanner");
        msg.setMatchedSectors(Collections.singletonList("travel"));

        List<CompanyPlatformMappingMessage> results = mapper.map(msg);
        assertNotNull(results);
        assertFalse(results.isEmpty());

        CompanyPlatformMappingMessage mapping = results.get(0);
        List<String> platforms = mapping.getInferredAdPlatforms();
        assertTrue(platforms.contains("bing_ads") || platforms.contains("skyscanner_ads"),
                "Travel sector should map to travel-related platforms");
    }

    // ---------------------------------------------------------------
    // 14. Empty sector list → returns empty
    // ---------------------------------------------------------------
    @Test
    void testMapWithNoMatchedSectors() {
        SourceSectorMappingMessage msg = new SourceSectorMappingMessage();
        msg.setSourceName("test_source");
        msg.setMatchedSectors(Collections.<String>emptyList());

        List<CompanyPlatformMappingMessage> results = mapper.map(msg);
        assertNotNull(results);
        assertTrue(results.isEmpty(), "No matched sectors should yield no company mappings");
    }
}
