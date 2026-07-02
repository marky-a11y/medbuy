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
class RedditApiWrapperTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private AdPlatformRateLimiter rateLimiter;
    @Mock
    private OAuthTokenManager tokenManager;

    private MeterRegistry meterRegistry;
    private RedditApiWrapper wrapper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        wrapper = new RedditApiWrapper(
                "", "TestAgent/1.0", restTemplate, rateLimiter, tokenManager, meterRegistry);
    }

    @Test
    void testMockEnabledReturnsMockData() {
        RawSourceData result = wrapper.fetchSubredditPosts("technology", "hot", 25);
        assertNotNull(result);
        assertEquals("reddit", result.getSourceName());
        assertEquals("MOCK", result.getFetchStatus());
        assertTrue(result.getRecordCount() > 0);
        assertNotNull(result.getRawPayload());
    }

    @Test
    void testNoApiKeyReturnsMockData() {
        RedditApiWrapper noMock = new RedditApiWrapper(
                "", "Agent", restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = noMock.fetchSubredditPosts("marketing", "new", 10);
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }

    @Test
    void testMockDisabledWithApiKeyReturnsMockFallback() {
        RedditApiWrapper hasKey = new RedditApiWrapper(
                "key", "Agent", restTemplate, rateLimiter, tokenManager, meterRegistry);
        RawSourceData result = hasKey.fetchSubredditPosts("business", "top", 5);
        assertNotNull(result);
        assertEquals("MOCK", result.getFetchStatus());
    }
}
