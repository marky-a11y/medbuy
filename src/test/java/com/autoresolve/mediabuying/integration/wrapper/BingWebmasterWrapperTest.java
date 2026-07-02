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
class BingWebmasterWrapperTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private AdPlatformRateLimiter rateLimiter;
    @Mock
    private OAuthTokenManager tokenManager;

    private MeterRegistry meterRegistry;
    private BingWebmasterWrapper wrapper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        wrapper = new BingWebmasterWrapper(
                "", "", restTemplate, rateLimiter, tokenManager, meterRegistry);
    }

    @Test
    void testMockEnabledReturnsMockData() {
        RawSourceData result = wrapper.fetchSearchTraffic("https://example.com");
        assertNotNull(result);
        assertEquals("bing_webmaster", result.getSourceName());
        assertEquals("MOCK", result.getFetchStatus());
        assertTrue(result.getRecordCount() > 0);
        assertNotNull(result.getRawPayload());
        assertTrue(result.getNormalizedSummary().contains("Bing"));
    }

    @Test
    void testNoApiKeyReturnsMockData() {
        BingWebmasterWrapper noMock = new BingWebmasterWrapper(
                "", "", restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = noMock.fetchSearchTraffic("https://test.com");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }

    @Test
    void testMockDisabledWithApiKeyReturnsMockFallback() {
        BingWebmasterWrapper hasKey = new BingWebmasterWrapper(
                "key", "https://mysite.com",
                restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = hasKey.fetchSearchTraffic("");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }
}
