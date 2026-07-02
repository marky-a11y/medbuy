package com.autoresolve.mediabuying.integration.wrapper;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared utility providing deterministic mock-data helpers for all 10 source wrappers.
 * <p>
 * This is a stateless utility class (not a Spring bean). All methods are static.
 * Use {@link #deterministicSeed(String, String)} when reproducible mock output
 * is needed for testing or local development.
 * </p>
 */
public final class MockDataHelper {

    private MockDataHelper() {
        // utility class — no instantiation
    }

    /**
     * Picks a random element from the given list.
     *
     * @param list the source list; must not be null or empty
     * @param <T>  element type
     * @return a randomly selected element
     */
    public static <T> T randomFromList(List<T> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /**
     * Returns a random integer in {@code [min, max]} (inclusive on both ends).
     */
    public static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Returns a random price with two decimal places between $10.00 and $499.99.
     */
    public static double randomPrice() {
        return Math.round(ThreadLocalRandom.current().nextDouble(10.0, 500.0) * 100.0) / 100.0;
    }

    /**
     * Creates a {@link Random} instance seeded with a deterministic hash of
     * the source name and date string, so that the same source + date always
     * produces the same sequence of "random" values.
     *
     * @param sourceName e.g. "yelp_fusion"
     * @param date       date string, e.g. "2026-06-30"
     * @return a seeded {@link Random} instance
     */
    public static Random deterministicSeed(String sourceName, String date) {
        long seed = (sourceName + "|" + date).hashCode();
        return new Random(seed);
    }

    /**
     * Generates a unique ingestion key from the source name and the current
     * wall-clock time (epoch millis).
     * <p>
     * Format: {@code <sourceName>_<epochMillis>}
     * </p>
     */
    public static String generateIngestionKey(String sourceName) {
        return sourceName + "_" + Instant.now().toEpochMilli();
    }
}
