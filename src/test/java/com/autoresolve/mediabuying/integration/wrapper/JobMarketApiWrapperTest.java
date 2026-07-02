package com.autoresolve.mediabuying.integration.wrapper;

import com.autoresolve.mediabuying.integration.auth.OAuthTokenManager;
import com.autoresolve.mediabuying.integration.dto.RawSourceData;
import com.autoresolve.mediabuying.integration.ratelimit.AdPlatformRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JobMarketApiWrapperTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private AdPlatformRateLimiter rateLimiter;
    @Mock
    private OAuthTokenManager tokenManager;

    private MeterRegistry meterRegistry;
    private JobMarketApiWrapper wrapper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        wrapper = new JobMarketApiWrapper(
                "", "", "adzuna",
                restTemplate, rateLimiter, tokenManager, meterRegistry);
    }

    @Test
    void testMockEnabledReturnsMockData() {
        RawSourceData result = wrapper.fetchJobListings("software engineer", "New York");
        assertNotNull(result);
        assertEquals("job_market", result.getSourceName());
        assertEquals("MOCK", result.getFetchStatus());
        assertTrue(result.getRecordCount() > 0);
        assertNotNull(result.getRawPayload());
    }

    @Test
    void testNoApiKeyReturnsMockData() {
        JobMarketApiWrapper noMock = new JobMarketApiWrapper(
                "", "", "adzuna",
                restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = noMock.fetchJobListings("analyst", "Chicago");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }

    @Test
    void testMockDisabledWithApiKeyReturnsMockFallback() {
        JobMarketApiWrapper hasKey = new JobMarketApiWrapper(
                "key", "app123", "adzuna",
                restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = hasKey.fetchJobListings("nurse", "Houston");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }

    @Test
    void testCustomProvider() {
        JobMarketApiWrapper customProvider = new JobMarketApiWrapper(
                "key", "app456", "indeed",
                restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = customProvider.fetchJobListings("driver", "Dallas");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
        assertTrue(result.getNormalizedSummary().contains("job_market"));
    }
}
