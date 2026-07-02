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
class SkyscannerApiWrapperTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private AdPlatformRateLimiter rateLimiter;
    @Mock
    private OAuthTokenManager tokenManager;

    private MeterRegistry meterRegistry;
    private SkyscannerApiWrapper wrapper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        wrapper = new SkyscannerApiWrapper(
                "", restTemplate, rateLimiter, tokenManager, meterRegistry);
    }

    @Test
    void testMockEnabledReturnsMockData() {
        RawSourceData result = wrapper.fetchFlightPrices("JFK", "LAX", "2026-08-15");
        assertNotNull(result);
        assertEquals("skyscanner", result.getSourceName());
        assertEquals("MOCK", result.getFetchStatus());
        assertTrue(result.getRecordCount() > 0);
        assertNotNull(result.getRawPayload());
    }

    @Test
    void testNoApiKeyReturnsMockData() {
        SkyscannerApiWrapper noMock = new SkyscannerApiWrapper(
                "", restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = noMock.fetchFlightPrices("LHR", "CDG", "2026-09-01");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }

    @Test
    void testMockDisabledWithApiKeyReturnsMockFallback() {
        SkyscannerApiWrapper hasKey = new SkyscannerApiWrapper(
                "key", restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = hasKey.fetchFlightPrices("ORD", "DFW", "2026-07-04");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }
}
