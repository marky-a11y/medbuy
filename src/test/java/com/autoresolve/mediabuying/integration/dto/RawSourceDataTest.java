package com.autoresolve.mediabuying.integration.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RawSourceData} DTO — builder, equals, toString.
 */
class RawSourceDataTest {

    @Test
    void testBuilderCreatesObject() {
        Instant now = Instant.now();
        RawSourceData data = RawSourceData.builder()
                .sourceName("yelp_fusion")
                .sourceUrl("https://api.yelp.com/v3/businesses/search")
                .sourceType("MOCK")
                .rawPayload("{\"test\":\"value\"}")
                .normalizedSummary("Test summary")
                .recordCount(5)
                .fetchStatus("MOCK")
                .fetchTimestamp(now)
                .ingestionKey("yelp_fusion_1234567890")
                .licenseType("PUBLIC")
                .build();

        assertEquals("yelp_fusion", data.getSourceName());
        assertEquals("MOCK", data.getSourceType());
        assertEquals("{\"test\":\"value\"}", data.getRawPayload());
        assertEquals("Test summary", data.getNormalizedSummary());
        assertEquals(5, data.getRecordCount());
        assertEquals("MOCK", data.getFetchStatus());
        assertEquals(now, data.getFetchTimestamp());
        assertEquals("yelp_fusion_1234567890", data.getIngestionKey());
        assertEquals("PUBLIC", data.getLicenseType());
    }

    @Test
    void testNoArgsConstructor() {
        RawSourceData data = new RawSourceData();
        assertNull(data.getSourceName());
        assertNull(data.getSourceType());
        assertNull(data.getRawPayload());
        assertNull(data.getFetchStatus());
        assertEquals(0, data.getRecordCount());
    }

    @Test
    void testAllArgsConstructor() {
        Instant now = Instant.now();
        RawSourceData data = new RawSourceData(
                "pytrends",
                "https://trends.google.com",
                "API",
                "{}",
                "Summary",
                10,
                "SUCCESS",
                now,
                "pytrends_123",
                "PUBLIC"
        );

        assertEquals("pytrends", data.getSourceName());
        assertEquals("API", data.getSourceType());
        assertEquals("{}", data.getRawPayload());
        assertEquals("Summary", data.getNormalizedSummary());
        assertEquals(10, data.getRecordCount());
        assertEquals("SUCCESS", data.getFetchStatus());
        assertEquals(now, data.getFetchTimestamp());
        assertEquals("pytrends_123", data.getIngestionKey());
        assertEquals("PUBLIC", data.getLicenseType());
    }

    @Test
    void testSettersAndGetters() {
        RawSourceData data = new RawSourceData();
        data.setSourceName("ebay");
        data.setRecordCount(42);
        data.setFetchStatus("PARTIAL");
        data.setLicenseType("PROPRIETARY");

        assertEquals("ebay", data.getSourceName());
        assertEquals(42, data.getRecordCount());
        assertEquals("PARTIAL", data.getFetchStatus());
        assertEquals("PROPRIETARY", data.getLicenseType());
    }

    @Test
    void testEqualsAndHashCode() {
        Instant now = Instant.now();
        RawSourceData data1 = RawSourceData.builder()
                .sourceName("test").fetchTimestamp(now).ingestionKey("key_1")
                .fetchStatus("MOCK").sourceType("MOCK").recordCount(0)
                .licenseType("PUBLIC")
                .build();
        RawSourceData data2 = RawSourceData.builder()
                .sourceName("test").fetchTimestamp(now).ingestionKey("key_1")
                .fetchStatus("MOCK").sourceType("MOCK").recordCount(0)
                .licenseType("PUBLIC")
                .build();

        assertEquals(data1, data2);
        assertEquals(data1.hashCode(), data2.hashCode());
    }

    @Test
    void testToString() {
        RawSourceData data = RawSourceData.builder()
                .sourceName("skyscanner")
                .fetchStatus("MOCK")
                .build();
        String str = data.toString();
        assertTrue(str.contains("skyscanner"));
        assertTrue(str.contains("MOCK"));
    }
}
