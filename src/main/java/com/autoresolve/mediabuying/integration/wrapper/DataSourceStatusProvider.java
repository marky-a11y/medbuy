package com.autoresolve.mediabuying.integration.wrapper;

/**
 * Interface implemented by all data-source wrappers to expose their
 * runtime status — whether they are fetching LIVE data from the external
 * API or returning MOCK (synthetic) data.
 * <p>
 * The status is auto-detected at construction time: if the required API
 * credentials are present and a lightweight connectivity test succeeds,
 * the wrapper operates in LIVE mode. Otherwise it falls back to MOCK.
 * </p>
 */
public interface DataSourceStatusProvider {

    /**
     * Human-readable name of the source, e.g. "pytrends", "yelp_fusion".
     */
    String getSourceName();

    /**
     * Returns {@code "LIVE"} if the wrapper is connected to the real API,
     * or {@code "MOCK"} if it is returning synthetic data (fallback).
     */
    default String getDataSourceType() {
        return isLive() ? "LIVE" : "MOCK";
    }

    /**
     * Returns {@code true} when the wrapper is successfully connected to
     * the live external API and returning real data.
     */
    boolean isLive();
}
