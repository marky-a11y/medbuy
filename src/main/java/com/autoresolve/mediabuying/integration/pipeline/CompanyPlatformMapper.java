package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.messaging.dto.CompanyPlatformMappingMessage;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage&#x2083 mapper that extracts company/brand names from a
 * {@link SourceSectorMappingMessage} and infers the most likely advertising
 * platform(s) those companies use.
 * <p>
 * Extraction and platform-inference logic is driven by configuration
 * properties under {@code company-platform-mapping.*}.
 */
@Component
@ConfigurationProperties(prefix = "company-platform-mapping")
public class CompanyPlatformMapper {

    private static final Logger log = LoggerFactory.getLogger(CompanyPlatformMapper.class);

    /** Source name (lowercase) → regex pattern used to extract company names. */
    private Map<String, String> sourceExtractionPatterns = new HashMap<>();

    /**
     * Business-type (e.g. {@code "e-commerce"}) → list of inferred ad-platform
     * names.  The business type is derived from the sector name.
     */
    private Map<String, List<String>> businessTypeToPlatform = new HashMap<>();

    /**
     * Sector name → default platform when no business-type mapping matches.
     */
    private Map<String, String> defaultPlatformPerSector = new HashMap<>();

    // ---- canned brand keywords for Reddit extraction (simple MVP) ----
    private static final List<String> REDDIT_BRAND_KEYWORDS = new ArrayList<>();
    static {
        REDDIT_BRAND_KEYWORDS.add("Tesla");
        REDDIT_BRAND_KEYWORDS.add("Apple");
        REDDIT_BRAND_KEYWORDS.add("Google");
        REDDIT_BRAND_KEYWORDS.add("Microsoft");
        REDDIT_BRAND_KEYWORDS.add("Amazon");
        REDDIT_BRAND_KEYWORDS.add("Facebook");
        REDDIT_BRAND_KEYWORDS.add("Meta");
        REDDIT_BRAND_KEYWORDS.add("Netflix");
        REDDIT_BRAND_KEYWORDS.add("Spotify");
        REDDIT_BRAND_KEYWORDS.add("Uber");
        REDDIT_BRAND_KEYWORDS.add("Airbnb");
        REDDIT_BRAND_KEYWORDS.add("Shopify");
    }

    /**
     * Map a single {@link SourceSectorMappingMessage} to zero or more
     * {@link CompanyPlatformMappingMessage}s.
     *
     * @param msg the sector-grouped message; may be {@code null}
     * @return a list of company→platform mappings (never {@code null})
     */
    public List<CompanyPlatformMappingMessage> map(SourceSectorMappingMessage msg) {
        if (msg == null) {
            log.warn("Received null SourceSectorMappingMessage — returning empty list");
            return Collections.emptyList();
        }

        String sourceName = msg.getSourceName();
        String sectorName = extractFirstSector(msg);

        // Guard: no matched sectors → cannot determine business type
        if (sectorName == null) {
            log.info("No matched sectors for source '{}' — returning empty list", sourceName);
            return Collections.emptyList();
        }

        String normalizedSummary = extractNormalizedSummary(msg);

        // Phase 1: Extract company/brand names
        List<String> companies = extractCompanies(sourceName, normalizedSummary);
        if (companies.isEmpty()) {
            // Fall back to source name as proxy company name (MVP)
            companies = Collections.singletonList(sourceName);
            log.debug("Using source name '{}' as proxy company name for sector '{}'",
                    sourceName, sectorName);
        }

        // Phase 2: Infer business type & platform(s)
        String businessType = inferBusinessType(sectorName);
        List<String> inferredPlatforms = inferPlatforms(businessType, sectorName);

        // Phase 3: Build mapping messages
        List<CompanyPlatformMappingMessage> results = new ArrayList<>();
        for (String company : companies) {
            CompanyPlatformMappingMessage mapping = new CompanyPlatformMappingMessage();
            mapping.setEventId(UUID.randomUUID().toString());
            mapping.setCompanyName(company);
            mapping.setSectorName(sectorName);
            mapping.setSourceName(sourceName);
            mapping.setInferredAdPlatforms(inferredPlatforms);
            mapping.setMappingMethod("HEURISTIC");
            mapping.setConfidenceScore(computeConfidence(businessType, inferredPlatforms.size()));
            mapping.setProcessingTimestamp(java.time.Instant.now());
            results.add(mapping);
        }

        log.debug("Mapped {} company(ies) from source '{}': {}", results.size(), sourceName, companies);
        return results;
    }

    // ================ extraction helpers ================

    /**
     * Extract company/brand names from the given source using the configured
     * extraction patterns.
     */
    List<String> extractCompanies(String sourceName, String normalizedSummary) {
        if (sourceName == null) {
            return Collections.emptyList();
        }
        String lowerSource = sourceName.toLowerCase().trim();

        // Try configured regex pattern first
        String patternStr = sourceExtractionPatterns.get(lowerSource);
        if (patternStr != null) {
            if ("source-name".equals(patternStr)) {
                return Collections.singletonList(sourceName);
            }
            if ("keyword-as-company".equals(patternStr)) {
                if (normalizedSummary != null) {
                    String firstWord = extractFirstWord(normalizedSummary);
                    if (firstWord != null) {
                        return Collections.singletonList(firstWord);
                    }
                }
                return Collections.singletonList(sourceName);
            }

            // Actual regex pattern — try extraction; if no matches fall through
            if (normalizedSummary != null) {
                List<String> extracted = extractByPattern(normalizedSummary, patternStr);
                if (!extracted.isEmpty()) {
                    return extracted;
                }
            }
        }

        // Fallback: heuristic extraction per known source
        if ("ebay".equals(lowerSource) && normalizedSummary != null) {
            return extractEbaySellers(normalizedSummary);
        }
        if ("yelp".equals(lowerSource) || "yelp-fusion".equals(lowerSource)) {
            if (normalizedSummary != null) {
                return extractYelpBusiness(normalizedSummary);
            }
            return Collections.emptyList();
        }
        if ("reddit".equals(lowerSource) && normalizedSummary != null) {
            return extractRedditBrands(normalizedSummary);
        }
        if ("pytrends".equals(lowerSource) && normalizedSummary != null) {
            String firstWord = extractFirstWord(normalizedSummary);
            if (firstWord != null) {
                return Collections.singletonList(firstWord);
            }
        }

        // Default: use source name as proxy
        return Collections.singletonList(sourceName);
    }

    private String extractFirstWord(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx > 0) {
            return trimmed.substring(0, spaceIdx);
        }
        return trimmed;
    }

    private List<String> extractByPattern(String text, String patternStr) {
        List<String> results = new ArrayList<>();
        try {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String group = matcher.group(1);
                if (group != null && !group.trim().isEmpty()) {
                    results.add(group.trim());
                }
            }
        } catch (Exception e) {
            log.warn("Regex pattern '{}' failed on text: {}", patternStr, e.getMessage());
        }
        return results;
    }

    /**
     * eBay: extract text between "seller:" (case-insensitive) and the next
     * punctuation or end-of-string.
     */
    private List<String> extractEbaySellers(String text) {
        List<String> sellers = new ArrayList<>();
        Pattern p = Pattern.compile("seller:\\s*([\\w\\s]+?)(?:[\\.,;!?]|$)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        while (m.find()) {
            String seller = m.group(1).trim();
            if (!seller.isEmpty()) {
                sellers.add(seller);
            }
        }
        // Fallback: if no seller: pattern found, return the whole summary as proxy
        if (sellers.isEmpty()) {
            sellers.add(text.trim());
        }
        return sellers;
    }

    /**
     * Yelp: first line of normalizedSummary is the business name.
     */
    private List<String> extractYelpBusiness(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String trimmed = text.trim();
        int newlineIdx = trimmed.indexOf('\n');
        String firstLine;
        if (newlineIdx > 0) {
            firstLine = trimmed.substring(0, newlineIdx).trim();
        } else {
            // Also try splitting on period or comma for first "sentence"
            int periodIdx = trimmed.indexOf('.');
            if (periodIdx > 0) {
                firstLine = trimmed.substring(0, periodIdx).trim();
            } else {
                firstLine = trimmed;
            }
        }
        if (!firstLine.isEmpty()) {
            return Collections.singletonList(firstLine);
        }
        return Collections.emptyList();
    }

    /**
     * Reddit: scan for known brand keywords in the text.
     */
    private List<String> extractRedditBrands(String text) {
        List<String> found = new ArrayList<>();
        String lowerText = text.toLowerCase();
        for (String brand : REDDIT_BRAND_KEYWORDS) {
            // Check case-insensitive
            int idx = 0;
            while ((idx = lowerText.indexOf(brand.toLowerCase(), idx)) != -1) {
                found.add(brand);
                idx += brand.length();
            }
        }
        return found;
    }

    // ================ platform inference helpers ================

    /**
     * Map a sector name to a business type string used for platform lookups.
     */
    String inferBusinessType(String sectorName) {
        if (sectorName == null) {
            return "default";
        }
        String lower = sectorName.toLowerCase().trim();
        if ("retail".equals(lower)) {
            return "e-commerce";
        }
        if ("technology".equals(lower) || "finance".equals(lower)) {
            return "b2b-saas";
        }
        if ("travel".equals(lower)) {
            return "travel";
        }
        if ("job-market".equals(lower)) {
            return "job-market";
        }
        if ("health-wellness".equals(lower)) {
            return "local-business";
        }
        if ("manufacturing".equals(lower)) {
            return "b2b-saas";
        }
        // Yelp/Foursquare sources typically indicate local business
        return "default";
    }

    /**
     * Infer ad platforms for a given business type and sector name.
     */
    List<String> inferPlatforms(String businessType, String sectorName) {
        // 1. Check businessType → platform mapping
        if (businessType != null && businessTypeToPlatform.containsKey(businessType)) {
            return new ArrayList<>(businessTypeToPlatform.get(businessType));
        }

        // 2. Check defaultPlatformPerSector
        if (sectorName != null && defaultPlatformPerSector.containsKey(sectorName)) {
            return Collections.singletonList(defaultPlatformPerSector.get(sectorName));
        }

        // 3. Ultimate fallback
        return Collections.singletonList("google_ads");
    }

    /**
     * Compute a confidence score in [0.0, 1.0].
     */
    double computeConfidence(String businessType, int platformCount) {
        if (businessType == null) {
            return 0.3;
        }
        switch (businessType) {
            case "e-commerce":
                return 0.85;
            case "b2b-saas":
                return 0.75;
            case "travel":
                return 0.80;
            case "job-market":
                return 0.70;
            case "local-business":
                return 0.65;
            default:
                return 0.50;
        }
    }

    // ================ internal helpers ================

    private String extractFirstSector(SourceSectorMappingMessage msg) {
        if (msg.getMatchedSectors() != null && !msg.getMatchedSectors().isEmpty()) {
            return msg.getMatchedSectors().get(0);
        }
        return null;
    }

    private String extractNormalizedSummary(SourceSectorMappingMessage msg) {
        // SourceSectorMappingMessage doesn't carry normalizedSummary directly.
        // For the MVP, we use the sourceName as the primary extraction cue.
        // The actual raw/normalized data would need to be looked up from the
        // original event — we rely on the configuration patterns instead.
        return msg.getSourceName();
    }

    // ---- configuration property accessors ----

    public Map<String, String> getSourceExtractionPatterns() {
        return sourceExtractionPatterns;
    }

    public void setSourceExtractionPatterns(Map<String, String> sourceExtractionPatterns) {
        this.sourceExtractionPatterns = sourceExtractionPatterns;
    }

    public Map<String, List<String>> getBusinessTypeToPlatform() {
        return businessTypeToPlatform;
    }

    public void setBusinessTypeToPlatform(Map<String, List<String>> businessTypeToPlatform) {
        this.businessTypeToPlatform = businessTypeToPlatform;
    }

    public Map<String, String> getDefaultPlatformPerSector() {
        return defaultPlatformPerSector;
    }

    public void setDefaultPlatformPerSector(Map<String, String> defaultPlatformPerSector) {
        this.defaultPlatformPerSector = defaultPlatformPerSector;
    }
}
