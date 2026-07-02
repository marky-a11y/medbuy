package com.autoresolve.mediabuying.integration.wrapper;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MockDataHelper}.
 */
class MockDataHelperTest {

    @Test
    void testRandomFromList() {
        List<String> items = Arrays.asList("alpha", "beta", "gamma");
        String chosen = MockDataHelper.randomFromList(items);
        assertTrue(items.contains(chosen));
    }

    @Test
    void testRandomIntInRange() {
        for (int i = 0; i < 100; i++) {
            int val = MockDataHelper.randomInt(5, 10);
            assertTrue(val >= 5 && val <= 10, "Value " + val + " out of range [5,10]");
        }
    }

    @Test
    void testRandomPriceInRange() {
        for (int i = 0; i < 100; i++) {
            double price = MockDataHelper.randomPrice();
            assertTrue(price >= 10.0 && price <= 500.0, "Price " + price + " out of range [10,500]");
        }
    }

    @Test
    void testDeterministicSeedProducesSameSequence() {
        Random r1 = MockDataHelper.deterministicSeed("yelp_fusion", "2026-06-30");
        Random r2 = MockDataHelper.deterministicSeed("yelp_fusion", "2026-06-30");

        for (int i = 0; i < 20; i++) {
            assertEquals(r1.nextInt(), r2.nextInt());
        }
    }

    @Test
    void testDeterministicSeedDifferentSourceDifferentSequence() {
        Random r1 = MockDataHelper.deterministicSeed("yelp_fusion", "2026-06-30");
        Random r2 = MockDataHelper.deterministicSeed("ebay", "2026-06-30");

        boolean allSame = true;
        for (int i = 0; i < 10; i++) {
            if (r1.nextInt() != r2.nextInt()) {
                allSame = false;
                break;
            }
        }
        assertFalse(allSame, "Different sources should produce different sequences");
    }

    @Test
    void testGenerateIngestionKeyFormat() {
        String key = MockDataHelper.generateIngestionKey("pytrends");
        assertTrue(key.startsWith("pytrends_"));
        assertTrue(key.length() > "pytrends_".length());
    }

    @Test
    void testGenerateIngestionKeyUniquePerCall() {
        String key1 = MockDataHelper.generateIngestionKey("test");
        String key2 = MockDataHelper.generateIngestionKey("test");
        assertNotEquals(key1, key2);
    }
}
