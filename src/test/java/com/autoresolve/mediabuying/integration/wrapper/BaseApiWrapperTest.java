package com.autoresolve.mediabuying.integration.wrapper;

import com.autoresolve.mediabuying.exception.IntegrationUnavailableException;
import com.autoresolve.mediabuying.integration.auth.OAuthTokenManager;
import com.autoresolve.mediabuying.integration.ratelimit.AdPlatformRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BaseApiWrapperTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AdPlatformRateLimiter rateLimiter;

    @Mock
    private OAuthTokenManager tokenManager;

    private TestApiWrapper wrapper;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        wrapper = new TestApiWrapper(restTemplate, rateLimiter, tokenManager, meterRegistry);
    }

    @Test
    void testSuccessfulCallReturnsResult() {
        Supplier<String> successfulCall = () -> "success";
        String result = wrapper.executeWithRetry(successfulCall, "TestPlatform");
        assertEquals("success", result);
        verify(rateLimiter).acquire("TestPlatform");
    }

    @Test
    void testRetryExhaustsAfterThreeFailures() {
        Supplier<String> failingCall = () -> {
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        };

        assertThrows(IntegrationUnavailableException.class,
                () -> wrapper.executeWithRetry(failingCall, "TestPlatform"));

        // Rate limiter should be called for each attempt
        verify(rateLimiter, times(3)).acquire("TestPlatform");
    }

    @Test
    void testRecoversAfterRetry() {
        final int[] attempts = {0};
        Supplier<String> flakyCall = () -> {
            attempts[0]++;
            if (attempts[0] < 2) {
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return "recovered";
        };

        String result = wrapper.executeWithRetry(flakyCall, "TestPlatform");
        assertEquals("recovered", result);
        assertEquals(2, attempts[0]);
        verify(rateLimiter, times(2)).acquire("TestPlatform");
    }

    @Test
    void testClientErrorThrowsImmediately() {
        Supplier<String> clientErrorCall = () -> {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
        };

        assertThrows(IntegrationUnavailableException.class,
                () -> wrapper.executeWithRetry(clientErrorCall, "TestPlatform"));

        verify(rateLimiter).acquire("TestPlatform");
    }

    @Test
    void testUnauthorizedTriggersTokenInvalidation() {
        Supplier<String> unauthorizedCall = () -> {
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        };

        // First call throws 401 -> token invalidation -> retry throws again
        assertThrows(IntegrationUnavailableException.class,
                () -> wrapper.executeWithRetry(unauthorizedCall, "TestPlatform"));

        // Token should be invalidated
        verify(tokenManager).invalidateToken("TestPlatform");
    }

    @Test
    void testUnauthorizedThenRecovers() {
        final int[] calls = {0};
        Supplier<String> flakyAuthCall = () -> {
            calls[0]++;
            if (calls[0] == 1) {
                throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
            }
            return "recovered";
        };

        String result = wrapper.executeWithRetry(flakyAuthCall, "TestPlatform");
        assertEquals("recovered", result);
        verify(tokenManager).invalidateToken("TestPlatform");
    }

    @Test
    void testTooManyRequestsAppliesExtendedBackoff() {
        Supplier<String> rateLimitedCall = () -> {
            throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
        };

        assertThrows(IntegrationUnavailableException.class,
                () -> wrapper.executeWithRetry(rateLimitedCall, "TestPlatform"));
    }

    /**
     * Test implementation of the abstract wrapper.
     */
    private static class TestApiWrapper extends BaseApiWrapper<String> {

        TestApiWrapper(RestTemplate restTemplate,
                       AdPlatformRateLimiter rateLimiter,
                       OAuthTokenManager tokenManager,
                       MeterRegistry meterRegistry) {
            super(restTemplate, rateLimiter, tokenManager, meterRegistry);
        }

        @Override
        public String executeWithRetry(Supplier<String> apiCall, String platformName) {
            return super.executeWithRetry(apiCall, platformName);
        }
    }
}
