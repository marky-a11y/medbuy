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
class FoursquarePlacesWrapperTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private AdPlatformRateLimiter rateLimiter;
    @Mock
    private OAuthTokenManager tokenManager;

    private MeterRegistry meterRegistry;
    private FoursquarePlacesWrapper wrapper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        wrapper = new FoursquarePlacesWrapper(
                "", restTemplate, rateLimiter, tokenManager, meterRegistry);
    }

    @Test
    void testMockEnabledReturnsMockData() {
        RawSourceData result = wrapper.fetchVenues("New York, NY", "retail");
        assertNotNull(result);
        assertEquals("foursquare_places", result.getSourceName());
        assertEquals("MOCK", result.getFetchStatus());
        assertTrue(result.getRecordCount() > 0);
        assertNotNull(result.getRawPayload());
    }

    @Test
    void testNoApiKeyReturnsMockData() {
        FoursquarePlacesWrapper noMock = new FoursquarePlacesWrapper(
                "", restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = noMock.fetchVenues("LA", "dining");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }

    @Test
    void testMockDisabledWithApiKeyReturnsMockFallback() {
        FoursquarePlacesWrapper hasKey = new FoursquarePlacesWrapper(
                "key", restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = hasKey.fetchVenues("Miami", "fitness");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }
}
