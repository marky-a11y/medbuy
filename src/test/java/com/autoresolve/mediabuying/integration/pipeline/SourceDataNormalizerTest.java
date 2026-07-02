package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.integration.dto.RawSourceData;
import com.autoresolve.mediabuying.messaging.dto.NormalizedSourceMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SourceDataNormalizer}.
 * <p>
 * Verifies field mapping, null handling, ID generation, and edge cases.
 * </p>
 */
class SourceDataNormalizerTest {

    private SourceDataNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new SourceDataNormalizer();
    }

    @Test
    void testNormalizeAllFieldsMappedCorrectly() {
        // Arrange
        Instant now = Instant.now();
        String ingestionKey = "test_source_1234567890";

        RawSourceData rawData = RawSourceData.builder()
                .sourceName("test_source")
                .sourceUrl("https://example.com/api")
                .sourceType("MOCK")
                .rawPayload("{\"key\":\"value\"}")
                .normalizedSummary("Test summary: 42 records")
                .recordCount(42)
                .fetchStatus("SUCCESS")
                .fetchTimestamp(now)
                .ingestionKey(ingestionKey)
                .licenseType("PUBLIC")
                .build();

        // Act
        NormalizedSourceMessage result = normalizer.normalize(rawData);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getEventId(), "eventId must not be null");
        assertTrue(result.getEventId().length() > 0, "eventId must not be empty");

        assertEquals("test_source", result.getSourceName());
        assertEquals("https://example.com/api", result.getSourceUrl());
        assertEquals("MOCK", result.getSourceType());
        assertEquals("{\"key\":\"value\"}", result.getRawData());
        assertEquals("Test summary: 42 records", result.getNormalizedSummary());
        assertEquals(now, result.getIngestionTimestamp());
        assertEquals(ingestionKey, result.getIngestionKey());
    }

    @Test
    void testNormalizeNullInputReturnsNull() {
        // Act
        NormalizedSourceMessage result = normalizer.normalize(null);

        // Assert
        assertNull(result, "normalize(null) must return null");
    }

    @Test
    void testNormalizeGeneratesUniqueEventId() {
        // Arrange
        RawSourceData rawData = RawSourceData.builder()
                .sourceName("unique_test")
                .sourceUrl("https://example.com")
                .sourceType("MOCK")
                .rawPayload("{}")
                .normalizedSummary("test")
                .recordCount(0)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey("key_1")
                .licenseType("PUBLIC")
                .build();

        // Act
        NormalizedSourceMessage first = normalizer.normalize(rawData);
        NormalizedSourceMessage second = normalizer.normalize(rawData);

        // Assert – same raw data should produce different eventIds
        assertNotNull(first.getEventId());
        assertNotNull(second.getEventId());
        assertNotEquals(first.getEventId(), second.getEventId(),
                "Each normalization must generate a unique eventId");
    }

    @Test
    void testNormalizeEventIdIsValidUuid() {
        // Arrange
        RawSourceData rawData = RawSourceData.builder()
                .sourceName("uuid_test")
                .sourceUrl("https://example.com")
                .sourceType("MOCK")
                .rawPayload("{}")
                .normalizedSummary("test")
                .recordCount(0)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey("key_uuid")
                .licenseType("PUBLIC")
                .build();

        // Act
        NormalizedSourceMessage result = normalizer.normalize(rawData);

        // Assert – the eventId string should be parseable as a UUID
        assertDoesNotThrow(() -> UUID.fromString(result.getEventId()),
                "eventId must be a valid UUID string");
    }

    @Test
    void testNormalizeHandlesNullFieldsInRawData() {
        // Arrange – rawData with null optional fields
        RawSourceData rawData = RawSourceData.builder()
                .sourceName("null_fields")
                .sourceUrl(null)       // explicitly null
                .sourceType(null)      // explicitly null
                .rawPayload(null)      // explicitly null
                .normalizedSummary(null)
                .recordCount(0)
                .fetchStatus("MOCK")
                .fetchTimestamp(null)  // explicitly null
                .ingestionKey(null)    // explicitly null
                .licenseType(null)
                .build();

        // Act
        NormalizedSourceMessage result = normalizer.normalize(rawData);

        // Assert – null fields should propagate as null (no NPE)
        assertNotNull(result);
        assertNull(result.getSourceUrl(), "sourceUrl should be null");
        assertNull(result.getSourceType(), "sourceType should be null");
        assertNull(result.getRawData(), "rawData should be null");
        assertNull(result.getNormalizedSummary(), "normalizedSummary should be null");
        assertNull(result.getIngestionTimestamp(), "ingestionTimestamp should be null");
        assertNull(result.getIngestionKey(), "ingestionKey should be null");
    }

    @Test
    void testNormalizeHandlesEmptyStrings() {
        // Arrange
        RawSourceData rawData = RawSourceData.builder()
                .sourceName("")
                .sourceUrl("")
                .sourceType("")
                .rawPayload("")
                .normalizedSummary("")
                .recordCount(0)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey("")
                .licenseType("")
                .build();

        // Act
        NormalizedSourceMessage result = normalizer.normalize(rawData);

        // Assert – empty strings should be preserved
        assertNotNull(result);
        assertEquals("", result.getSourceName());
        assertEquals("", result.getSourceUrl());
        assertEquals("", result.getSourceType());
        assertEquals("", result.getRawData());
        assertEquals("", result.getNormalizedSummary());
        assertEquals("", result.getIngestionKey());
    }

    @Test
    void testNormalizeTrimsNoFields() {
        // Arrange – values with leading/trailing whitespace
        RawSourceData rawData = RawSourceData.builder()
                .sourceName("  spaced_source  ")
                .sourceUrl("  https://example.com  ")
                .sourceType("  MOCK  ")
                .rawPayload("  {\"a\":1}  ")
                .normalizedSummary("  summary  ")
                .recordCount(0)
                .fetchStatus("MOCK")
                .fetchTimestamp(Instant.now())
                .ingestionKey("  key  ")
                .licenseType("  PUBLIC  ")
                .build();

        // Act
        NormalizedSourceMessage result = normalizer.normalize(rawData);

        // Assert – normalizer should NOT trim (preserves original values)
        assertNotNull(result);
        assertEquals("  spaced_source  ", result.getSourceName());
        assertEquals("  https://example.com  ", result.getSourceUrl());
        assertEquals("  MOCK  ", result.getSourceType());
        assertEquals("  {\"a\":1}  ", result.getRawData());
        assertEquals("  summary  ", result.getNormalizedSummary());
        assertEquals("  key  ", result.getIngestionKey());
    }
}
