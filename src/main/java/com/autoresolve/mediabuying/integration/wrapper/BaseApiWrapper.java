package com.autoresolve.mediabuying.integration.wrapper;

import com.autoresolve.mediabuying.exception.IntegrationUnavailableException;
import com.autoresolve.mediabuying.integration.auth.OAuthTokenManager;
import com.autoresolve.mediabuying.integration.ratelimit.AdPlatformRateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.function.Supplier;

/**
 * Abstract base class for all external API wrappers.
 * Provides retry, circuit-breaking, rate-limiting, OAuth token management,
 * and error translation.
 */
public abstract class BaseApiWrapper<T> {

    private static final Logger log = LoggerFactory.getLogger(BaseApiWrapper.class);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    protected final RestTemplate restTemplate;
    protected final AdPlatformRateLimiter rateLimiter;
    protected final OAuthTokenManager tokenManager;

    // Custom Micrometer metrics
    protected final Counter integrationSuccessCounter;
    protected final Counter integrationFailureCounter;

    /**
     * @deprecated Use {@link #BaseApiWrapper(RestTemplate, AdPlatformRateLimiter, OAuthTokenManager, MeterRegistry)}
     *             instead. This constructor is retained for backward compatibility only.
     */
    @Deprecated
    protected BaseApiWrapper(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.rateLimiter = null;
        this.tokenManager = null;
        this.integrationSuccessCounter = null;
        this.integrationFailureCounter = null;
    }

    protected BaseApiWrapper(RestTemplate restTemplate,
                             AdPlatformRateLimiter rateLimiter,
                             OAuthTokenManager tokenManager,
                             MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.rateLimiter = rateLimiter;
        this.tokenManager = tokenManager;

        // Register custom Micrometer metrics
        this.integrationSuccessCounter = Counter.builder("media_buying_integration_success_total")
                .description("Total successful integration API calls")
                .tag("class", this.getClass().getSimpleName())
                .register(meterRegistry);
        this.integrationFailureCounter = Counter.builder("media_buying_integration_failure_total")
                .description("Total failed integration API calls")
                .tag("class", this.getClass().getSimpleName())
                .register(meterRegistry);
    }

    /**
     * Template method for API calls with retry, rate limiting, OAuth handling,
     * and error translation.
     */
    protected T executeWithRetry(Supplier<T> apiCall, String platformName) {
        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;
        boolean tokenInvalidated = false;

        while (attempt < MAX_RETRIES) {
            // Acquire rate limiter permit before each attempt
            if (rateLimiter != null) {
                rateLimiter.acquire(platformName);
            }

            try {
                attempt++;
                log.debug("Calling {} API, attempt {}", platformName, attempt);
                T result = apiCall.get();
                log.debug("{} API call successful on attempt {}", platformName, attempt);
                // Record success metric
                try {
                    if (integrationSuccessCounter != null) {
                        integrationSuccessCounter.increment();
                    }
                } catch (Exception e) {
                    log.trace("Failed to record integration success metric", e);
                }
                return result;

            } catch (HttpServerErrorException e) {
                if (attempt >= MAX_RETRIES) {
                    log.error("{} API exhausted retries: status={}", platformName, e.getStatusCode());
                    recordIntegrationFailure();
                    throw new IntegrationUnavailableException(
                            "Data temporarily unavailable for " + platformName +
                                    " after " + MAX_RETRIES + " attempts");
                }
                log.warn("{} API attempt {}/{} failed: status={}, retrying in {}ms",
                        platformName, attempt, MAX_RETRIES, e.getStatusCode(), backoff);
                safeSleep(backoff);
                backoff *= 2;

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    if (!tokenInvalidated) {
                        log.error("{} API authentication failed (401)", platformName);
                        if (tokenManager != null) {
                            tokenManager.invalidateToken(platformName);
                            log.info("Invalidated OAuth token for {}; next retry will refresh", platformName);
                        }
                        tokenInvalidated = true;
                    }
                    // Continue to retry after invalidation
                } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("{} API rate limited (429), applying extended backoff: {}ms",
                            platformName, backoff * 4L);
                    safeSleep(backoff * 4L);
                    backoff *= 2;
                } else {
                    log.error("{} API client error: status={}, body={}",
                            platformName, e.getStatusCode(), e.getResponseBodyAsString());
                    throw new IntegrationUnavailableException(
                            "Data temporarily unavailable for " + platformName + " (configuration error)");
                }

            } catch (Exception e) {
                log.error("{} API unexpected error", platformName, e);
                recordIntegrationFailure();
                throw new IntegrationUnavailableException(
                        "Data temporarily unavailable for " + platformName);
            }
        }

        recordIntegrationFailure();
        throw new IntegrationUnavailableException("Data temporarily unavailable for " + platformName);
    }

    /**
     * Records an integration failure metric. Never throws — failures are silently logged at trace level.
     */
    private void recordIntegrationFailure() {
        try {
            if (integrationFailureCounter != null) {
                integrationFailureCounter.increment();
            }
        } catch (Exception e) {
            log.trace("Failed to record integration failure metric", e);
        }
    }

    /**
     * Stub for backward compatibility.
     * @deprecated OAuth token management is now handled by {@link OAuthTokenManager}.
     */
    @Deprecated
    protected void refreshOAuthToken() {
        log.warn("refreshOAuthToken() called on BaseApiWrapper; OAuthTokenManager should be used instead");
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
