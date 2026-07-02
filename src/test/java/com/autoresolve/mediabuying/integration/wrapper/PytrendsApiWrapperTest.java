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
class PytrendsApiWrapperTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private AdPlatformRateLimiter rateLimiter;
    @Mock
    private OAuthTokenManager tokenManager;

    private MeterRegistry meterRegistry;
    private PytrendsApiWrapper wrapper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        wrapper = new PytrendsApiWrapper(
                restTemplate, rateLimiter, tokenManager, meterRegistry);
    }

    @Test
    void testMockEnabledReturnsMockDataWithMockStatus() {
        RawSourceData result = wrapper.fetchTrends("digital marketing", "US");
        assertNotNull(result);
        assertEquals("pytrends", result.getSourceName());
        assertEquals("MOCK", result.getFetchStatus());
        assertEquals("MOCK", result.getSourceType());
        assertTrue(result.getRecordCount() > 0);
        assertNotNull(result.getRawPayload());
        assertNotNull(result.getIngestionKey());
    }

    @Test
    void testMockDisabledStillReturnsMockData() {
        // PyTrends has no real Java API, so mock-disabled still returns mock data.
        PytrendsApiWrapper noMock = new PytrendsApiWrapper(
                restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = noMock.fetchTrends("test", "US");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }
}
