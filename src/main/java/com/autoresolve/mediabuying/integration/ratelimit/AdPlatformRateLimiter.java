package com.autoresolve.mediabuying.integration.ratelimit;

import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-platform rate limiter for ad-platform API calls.
 * <p>
 * Uses Guava {@link RateLimiter} internally.
 * Rates are configured via {@code integration.rate-limit.*} in application.yml.
 * </p>
 */
@Component
public class AdPlatformRateLimiter implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(AdPlatformRateLimiter.class);

    /** Default rate (permits/sec) for unknown platforms. */
    private static final double DEFAULT_RATE = 1.0;

    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    private final double googleAdsRate;
    private final double metaAdsRate;
    private final double tiktokAdsRate;
    private final double linkedinAdsRate;
    private final double iheartRadioRate;

    public AdPlatformRateLimiter(
            @Value("${integration.rate-limit.google-ads:0.02}") double googleAdsRate,
            @Value("${integration.rate-limit.meta-ads:0.055}") double metaAdsRate,
            @Value("${integration.rate-limit.tiktok-ads:10.0}") double tiktokAdsRate,
            @Value("${integration.rate-limit.linkedin-ads:0.028}") double linkedinAdsRate,
            @Value("${integration.rate-limit.iheart-radio:0.28}") double iheartRadioRate) {
        log.info("=== PHASE: AdPlatformRateLimiter constructor at {} ===", System.currentTimeMillis());
        this.googleAdsRate = googleAdsRate;
        this.metaAdsRate = metaAdsRate;
        this.tiktokAdsRate = tiktokAdsRate;
        this.linkedinAdsRate = linkedinAdsRate;
        this.iheartRadioRate = iheartRadioRate;
    }

    @Override
    public void afterPropertiesSet() {
        limiters.put("google_ads", RateLimiter.create(googleAdsRate));
        limiters.put("meta_ads", RateLimiter.create(metaAdsRate));
        limiters.put("tiktok_ads", RateLimiter.create(tiktokAdsRate));
        limiters.put("linkedin_ads", RateLimiter.create(linkedinAdsRate));
        limiters.put("iheart_radio", RateLimiter.create(iheartRadioRate));
        log.info("AdPlatformRateLimiter initialized: google={}/s, meta={}/s, tiktok={}/s, linkedin={}/s, iheart={}/s",
                googleAdsRate, metaAdsRate, tiktokAdsRate, linkedinAdsRate, iheartRadioRate);
    }

    /**
     * Acquires a permit for the given platform, blocking if necessary.
     *
     * @param platformName the platform name (e.g. "google_ads")
     */
    public void acquire(String platformName) {
        RateLimiter limiter = getOrCreateLimiter(platformName);
        double waitTime = limiter.acquire();
        if (waitTime > 0.0) {
            log.debug("Rate limited for platform={}: waited {} ms",
                    platformName, String.format("%.0f", waitTime * 1000));
        }
    }

    /**
     * Tries to acquire a permit without blocking.
     *
     * @param platformName the platform name
     * @return true if the permit was acquired, false otherwise
     */
    public boolean tryAcquire(String platformName) {
        RateLimiter limiter = getOrCreateLimiter(platformName);
        return limiter.tryAcquire();
    }

    /**
     * Returns the current rate for a platform (permits/sec).
     */
    public double getRate(String platformName) {
        RateLimiter limiter = limiters.get(normalizeKey(platformName));
        if (limiter != null) {
            return limiter.getRate();
        }
        return DEFAULT_RATE;
    }

    private RateLimiter getOrCreateLimiter(String platformName) {
        String key = normalizeKey(platformName);
        return limiters.computeIfAbsent(key, k -> {
            log.debug("Creating rate limiter for unknown platform {} with default rate={}", k, DEFAULT_RATE);
            return RateLimiter.create(DEFAULT_RATE);
        });
    }

    private String normalizeKey(String raw) {
        return raw.trim().toLowerCase().replaceAll("\\s+", "_").replaceAll("-", "_");
    }
}
