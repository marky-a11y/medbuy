package com.autoresolve.mediabuying.cache;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static final String COMPOSITE_TOP = "composite:top";
    public static final String HIERARCHY_ALL = "hierarchy:all";
    public static final String METRICS_PREFIX = "metrics";
    public static final String PLATFORMS_LIST = "platforms:list";
    public static final String CLIENTS_TOP_PREFIX = "clients:top";
    public static final String CLIENTS_LIST_PREFIX = "clients:list";
    public static final String INSIGHTS_CLIENT_GAPS = "insights:client-gaps";

    public static String metricsKey(long platformId, long sectorId, int page, String sort) {
        return String.format("metrics:%d:%d:%d:%s", platformId, sectorId, page, sort);
    }

    public static String metricsWildcard(long platformId, long sectorId) {
        return String.format("metrics:%d:%d:*", platformId, sectorId);
    }

    public static String clientsTopKey(long sectorId) {
        return String.format("clients:top:%d", sectorId);
    }

    public static String clientsListKey(int page, int size, String sortField, String sortDir) {
        return String.format("clients:list:%d:%d:%s:%s", page, size, sortField, sortDir);
    }

    public static String clientsListWildcard() {
        return CLIENTS_LIST_PREFIX + ":*";
    }
}
