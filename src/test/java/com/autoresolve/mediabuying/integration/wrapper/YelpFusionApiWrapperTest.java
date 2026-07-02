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
class YelpFusionApiWrapperTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private AdPlatformRateLimiter rateLimiter;
    @Mock
    private OAuthTokenManager tokenManager;

    private MeterRegistry meterRegistry;
    private YelpFusionApiWrapper wrapper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        wrapper = new YelpFusionApiWrapper(
                "", restTemplate, rateLimiter, tokenManager, meterRegistry);
    }

    @Test
    void testMockEnabledReturnsMockData() {
        RawSourceData result = wrapper.fetchBusinesses("restaurants", "San Francisco, CA");
        assertNotNull(result);
        assertEquals("yelp_fusion", result.getSourceName());
        assertEquals("MOCK", result.getFetchStatus());
        assertTrue(result.getRecordCount() > 0);
        assertNotNull(result.getRawPayload());
    }

    @Test
    void testNoApiKeyReturnsMockData() {
        YelpFusionApiWrapper noMock = new YelpFusionApiWrapper(
                "", restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = noMock.fetchBusinesses("test", "NYC");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }

    @Test
    void testApiKeyPresentReturnsMockData() {
        YelpFusionApiWrapper hasKey = new YelpFusionApiWrapper(
                "key", restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = hasKey.fetchBusinesses("retail", "Chicago");
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }
}
