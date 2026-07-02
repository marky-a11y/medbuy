package com.autoresolve.mediabuying.integration.wrapper;

import com.autoresolve.mediabuying.util.UrlSanitizer;

import com.autoresolve.mediabuying.integration.auth.OAuthTokenManager;
import com.autoresolve.mediabuying.integration.dto.RawSourceData;
import com.autoresolve.mediabuying.integration.ratelimit.AdPlatformRateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wrapper for U.S. Census Bureau API (2020 Decennial Census).
 * <p>
 * API reference: https://www.census.gov/data/developers/data-sets/decennial-census.html
 * Auth: API key (query parameter {@code key}); free key available at
 *       {@code https://api.census.gov/data/key_signup.html}.
 * Endpoint: GET /data/2020/dec/pl
 * Response format: JSON array of arrays — first element is column headers,
 *                  subsequent elements are data rows.
 * </p>
 */
@Component
public class CensusApiWrapper extends BaseApiWrapper<RawSourceData> implements DataSourceStatusProvider {

    private static final Logger log = LoggerFactory.getLogger(CensusApiWrapper.class);

    private final String apiKey;
    private final boolean liveMode;

    public CensusApiWrapper(
            @Value("${integration.census-api.api-key:}") String apiKey,
            RestTemplate restTemplate,
            AdPlatformRateLimiter rateLimiter,
            OAuthTokenManager tokenManager,
            MeterRegistry meterRegistry) {
        super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        this.apiKey = apiKey;
        this.liveMode = apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public String getSourceName() {
        return "census_api";
    }

    @Override
    public boolean isLive() {
        return liveMode;
    }

    /**
     * Fetches U.S. Census demographic data for the given state.
     * <p>
     * Calls the 2020 Decennial Census PL file for all states, then filters
     * the result to the requested state. If the API key is missing or the
     * call fails, mock data is returned instead.
     *
     * @param state full state name (e.g. "California", "Texas") or
     *              {@code "*"} to return all states
     * @return RawSourceData with population and demographic data for the state
     */
    public RawSourceData fetchDemographics(String state) {
        if (!liveMode) {
            log.debug("Returning mock Census data for state={}", state);
            return buildMockResponse(state);
        }
        try {
            // 1. Build URL for the 2020 Decennial Census PL file
            //    Returns NAME, total population (P1_001N), and state FIPS code
            String baseUrl = "https://api.census.gov/data/2020/dec/pl?get=NAME,P1_001N&for=state:*&key="
                    + URLEncoder.encode(apiKey, "UTF-8");

            // 2. Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 3. Make the API call
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl, HttpMethod.GET, entity, String.class);
            String json = response.getBody();

            // 4. Parse JSON response — Census API returns array of arrays
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            if (!root.isArray() || root.size() < 2) {
                log.warn("{} API returned unexpected format — falling back to mock", getSourceName());
                return buildMockResponse(state);
            }

            // First element is the header row
            JsonNode headerRow = root.get(0);
            // Remaining elements are data rows
            int dataRowCount = root.size() - 1;

            // Build a map of headers -> index for flexible column access
            Map<String, Integer> headerIndex = new LinkedHashMap<>();
            for (int i = 0; i < headerRow.size(); i++) {
                headerIndex.put(headerRow.get(i).asText(), i);
            }

            // Find the matching state row
            JsonNode matchingRow = null;
            String matchedStateName = null;
            String matchedPopulation = null;

            for (int i = 1; i < root.size(); i++) {
                JsonNode row = root.get(i);
                String rowState = row.get(headerIndex.get("NAME")).asText();
                if ("*".equals(state) || rowState.equalsIgnoreCase(state)) {
                    matchingRow = row;
                    matchedStateName = rowState;
                    matchedPopulation = row.get(headerIndex.get("P1_001N")).asText();
                    break;
                }
            }

            if (matchingRow == null) {
                log.warn("State '{}' not found in Census API response — falling back to mock", state);
                return buildMockResponse(state);
            }

            String summary = "Real Census data for " + matchedStateName
                    + ": population " + matchedPopulation;

            return RawSourceData.builder()
                    .sourceName(getSourceName())
                    .sourceUrl(UrlSanitizer.sanitize(baseUrl))
                    .sourceType("API")
                    .rawPayload(json)
                    .normalizedSummary(summary)
                    .recordCount("*".equals(state) ? dataRowCount : 1)
                    .fetchStatus("SUCCESS")
                    .fetchTimestamp(Instant.now())
                    .ingestionKey(MockDataHelper.generateIngestionKey(getSourceName()))
                    .licenseType("PUBLIC")
                    .build();

        } catch (Exception e) {
            log.warn("{} API call failed for state '{}': {} — falling back to mock",
                    getSourceName(), state, e.getMessage());
            return buildMockResponse(state);
        }
    }

    private RawSourceData buildMockResponse(String state) {
        // Deterministic mock population data for a selection of states
        String[][] mockStates = {
                {"California", "39538223"},
                {"Texas", "29145505"},
                {"Florida", "21538187"},
                {"New York", "20201249"},
                {"Pennsylvania", "13002700"},
                {"Illinois", "12812508"},
                {"Ohio", "11799448"},
                {"Georgia", "10711908"},
                {"North Carolina", "10439388"},
                {"Michigan", "10077331"}
        };

        // Find the requested state, or use the first entry as default
        String matchedName = state;
        String matchedPop = "0";
        boolean found = false;
        for (String[] row : mockStates) {
            if (row[0].equalsIgnoreCase(state) || "*".equals(state)) {
                matchedName = row[0];
                matchedPop = row[1];
                found = true;
                break;
            }
        }
        if (!found && !"*".equals(state)) {
            // State not in our mock table; generate plausible data
            matchedName = state;
            matchedPop = String.valueOf(MockDataHelper.randomInt(500000, 10000000));
        }

        // Build mock JSON in Census array-of-arrays format
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("[[\"NAME\",\"P1_001N\",\"state\"]");

        if ("*".equals(state)) {
            // Return all mock states
            for (String[] row : mockStates) {
                jsonBuilder.append(",[\"")
                        .append(escapeJson(row[0])).append("\",\"")
                        .append(row[1]).append("\",\"")
                        .append(getStateFips(row[0])).append("\"]");
            }
        } else {
            jsonBuilder.append(",[\"")
                    .append(escapeJson(matchedName)).append("\",\"")
                    .append(matchedPop).append("\",\"")
                    .append("XX").append("\"]");
        }

        jsonBuilder.append("]");

        String json = jsonBuilder.toString();
        int recordCount = "*".equals(state) ? mockStates.length : 1;

        String summary = "Census mock data for " + matchedName + ": population " + matchedPop;

        return RawSourceData.builder()
                .sourceName("census_api")
                .sourceUrl("https://api.census.gov/data/2020/dec/pl?get=NAME,P1_001N&for=state:*")
                .sourceType("MOCK")
                .rawPayload(json)
                .normalizedSummary(summary)
                .recordCount(recordCount)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey(MockDataHelper.generateIngestionKey("census_api"))
                .licenseType("PUBLIC")
                .build();
    }

    /**
     * Returns a two-letter FIPS code for well-known mock states.
     */
    private static String getStateFips(String stateName) {
        switch (stateName.toLowerCase()) {
            case "california": return "06";
            case "texas": return "48";
            case "florida": return "12";
            case "new york": return "36";
            case "pennsylvania": return "42";
            case "illinois": return "17";
            case "ohio": return "39";
            case "georgia": return "13";
            case "north carolina": return "37";
            case "michigan": return "26";
            default: return "XX";
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
