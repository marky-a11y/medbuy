package com.autoresolve.mediabuying.integration.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages OAuth 2.0 tokens for all ad-platform integrations.
 * <p>
 * Tokens are cached in-memory using a {@link ConcurrentHashMap}.
 * When a token is expired or within 5 minutes of expiry,
 * a synchronous refresh is triggered.
 * </p>
 */
@Service
public class OAuthTokenManager implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenManager.class);

    /** How many seconds before expiry we consider the token stale. */
    private static final long REFRESH_BUFFER_SECONDS = 300L; // 5 minutes

    private final ConcurrentHashMap<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate;

    /** Per-platform OAuth configs, keyed by normalized platform name. */
    private final Map<String, PlatformOAuthConfig> platformConfigs = new ConcurrentHashMap<>();

    // Custom Micrometer metrics
    private final Counter tokenRefreshErrorsCounter;

    public OAuthTokenManager(RestTemplate restTemplate, MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;

        // Register custom Micrometer metrics
        this.tokenRefreshErrorsCounter = Counter.builder("media_buying_oauth_token_refresh_errors_total")
                .description("Total number of OAuth token refresh errors")
                .tag("component", "OAuthTokenManager")
                .register(meterRegistry);
    }

    /**
     * Programmatically register OAuth config for a platform.
     * Called by a configuration bean that reads from application.yml.
     */
    public void registerPlatform(String platformName, PlatformOAuthConfig config) {
        String key = normalizeKey(platformName);
        platformConfigs.put(key, config);
        // If a static access token was provided, seed the cache
        if (config.getAccessToken() != null && !config.getAccessToken().isEmpty()) {
            tokenCache.put(key, new TokenEntry(
                    config.getAccessToken(),
                    Instant.now().plusSeconds(3600L),
                    platformName));
            log.debug("Seeded token cache for platform={} from static access token", platformName);
        }
        log.debug("Registered OAuth config for platform={}", platformName);
    }

    @Override
    public void afterPropertiesSet() {
        log.info("OAuthTokenManager initialized with {} platform config(s)", platformConfigs.size());
        if (platformConfigs.isEmpty()) {
            log.warn("No OAuth platform configurations registered; tokens will need manual provisioning");
        }
    }

    /**
     * Returns a valid access token for the given platform.
     * <p>
     * If a cached token exists and is valid for at least 5 more minutes,
     * returns it immediately. Otherwise performs a synchronous refresh.
     * </p>
     *
     * @param platformName the platform name (e.g. "google_ads")
     * @return a valid access token string
     * @throws IllegalStateException if no OAuth config is available
     */
    public String getAccessToken(String platformName) {
        String key = normalizeKey(platformName);
        TokenEntry entry = tokenCache.get(key);

        if (entry != null && !isExpiringSoon(entry)) {
            log.debug("Cache hit for token: platform={}", platformName);
            return entry.getAccessToken();
        }

        // Synchronized per-platform refresh to avoid thundering herd
        synchronized (key.intern()) {
            // Double-check after acquiring lock
            entry = tokenCache.get(key);
            if (entry != null && !isExpiringSoon(entry)) {
                return entry.getAccessToken();
            }

            PlatformOAuthConfig config = platformConfigs.get(key);
            if (config == null) {
                log.warn("No OAuth configuration for platform={}", platformName);
                throw new IllegalStateException(
                        "No OAuth configuration for platform: " + platformName);
            }

            // Attempt refresh
            try {
                String newToken = doRefreshToken(key, config);
                Instant expiresAt = Instant.now().plusSeconds(3600L); // default 1h
                tokenCache.put(key, new TokenEntry(newToken, expiresAt, platformName));
                log.info("OAuth token refreshed for platform={}", platformName);
                return newToken;
            } catch (Exception e) {
                log.error("Failed to refresh OAuth token for platform={}", platformName, e);
                try {
                    tokenRefreshErrorsCounter.increment();
                } catch (Exception metricEx) {
                    log.trace("Failed to record OAuth token refresh error metric", metricEx);
                }
                // Fall back to configured access token if available
                if (config.getAccessToken() != null && !config.getAccessToken().isEmpty()) {
                    log.warn("Falling back to configured access token for platform={}", platformName);
                    return config.getAccessToken();
                }
                // Fall back to cached token even if expiring soon
                if (entry != null) {
                    log.warn("Falling back to cached (possibly expired) token for platform={}", platformName);
                    return entry.getAccessToken();
                }
                throw new IllegalStateException(
                        "Unable to obtain OAuth token for platform: " + platformName, e);
            }
        }
    }

    /**
     * Invalidates the cached token for a platform, forcing a refresh on the next call.
     * Typically called on HTTP 401 responses.
     */
    public void invalidateToken(String platformName) {
        String key = normalizeKey(platformName);
        TokenEntry removed = tokenCache.remove(key);
        if (removed != null) {
            log.info("Invalidated OAuth token for platform={}", platformName);
        }
    }

    /**
     * Performs the actual OAuth 2.0 token refresh via POST to the token endpoint.
     */
    private String doRefreshToken(String platformKey, PlatformOAuthConfig config) {
        String tokenUrl = config.getTokenUrl();
        if (tokenUrl == null || tokenUrl.isEmpty()) {
            // No token URL configured; if we have a static access token, use it
            if (config.getAccessToken() != null && !config.getAccessToken().isEmpty()) {
                return config.getAccessToken();
            }
            throw new IllegalStateException(
                    "No OAuth token endpoint configured for platform: " + platformKey);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", config.getClientId());
        body.add("client_secret", config.getClientSecret());
        body.add("refresh_token", config.getRefreshToken());

        if (config.getScope() != null && !config.getScope().isEmpty()) {
            body.add("scope", config.getScope());
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        if (response.getBody() != null && response.getBody().containsKey("access_token")) {
            String accessToken = (String) response.getBody().get("access_token");
            log.debug("OAuth refresh successful for platform={}", platformKey);
            return accessToken;
        }

        throw new IllegalStateException(
                "OAuth refresh response missing access_token for platform: " + platformKey);
    }

    private boolean isExpiringSoon(TokenEntry entry) {
        return Instant.now().plusSeconds(REFRESH_BUFFER_SECONDS)
                .isAfter(entry.getExpiresAt());
    }

    private String normalizeKey(String platformName) {
        return platformName.trim().toLowerCase().replaceAll("\\s+", "_");
    }

    // ============================================================
    // Inner types
    // ============================================================

    /**
     * Holds a cached access token with its expiry timestamp.
     */
    public static class TokenEntry {
        private final String accessToken;
        private final Instant expiresAt;
        private final String platform;

        public TokenEntry(String accessToken, Instant expiresAt, String platform) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
            this.platform = platform;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public String getPlatform() {
            return platform;
        }
    }

    /**
     * Configuration properties for a single platform's OAuth settings.
     */
    public static class PlatformOAuthConfig {
        private String clientId;
        private String clientSecret;
        private String refreshToken;
        private String accessToken;
        private String developerToken;
        private String tokenUrl;
        private String scope;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public String getDeveloperToken() { return developerToken; }
        public void setDeveloperToken(String developerToken) { this.developerToken = developerToken; }

        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
    }
}
