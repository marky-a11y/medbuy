package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.eventbus.EventBus;
import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.messaging.dto.NormalizedSourceMessage;
import com.autoresolve.mediabuying.messaging.dto.SourceSectorMappingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SectorGrouper}.
 * <p>
 * Verifies that {@code source.raw} events are correctly classified and that
 * the appropriate number of {@code sector.grouped} events are published.
 * Tests rawEventId linking, error isolation, and edge cases.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SectorGrouperTest {

    @Mock
    private SectorClassifier sectorClassifier;

    @Mock
    private EventBus eventBus;

    @Captor
    private ArgumentCaptor<SourceSectorMappingMessage> mappingCaptor;

    private SectorGrouper sectorGrouper;

    @BeforeEach
    void setUp() {
        sectorGrouper = new SectorGrouper(sectorClassifier, eventBus);
    }

    // ---------------------------------------------------------------
    // 1. Single sector match → exactly one sector.grouped event
    // ---------------------------------------------------------------
    @Test
    void testSingleSectorMatchPublishesOneEvent() {
        // Arrange
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setEventId("msg-001");
        msg.setSourceName("tech_source");

        IntegrationEvent event = new IntegrationEvent("source.raw", "msg-001", msg);

        SourceSectorMappingMessage techMapping = new SourceSectorMappingMessage();
        techMapping.setSourceName("tech_source");
        techMapping.setMatchedSectors(Collections.singletonList("technology"));
        techMapping.setClassificationMethod("KEYWORD_MATCH");
        techMapping.setConfidenceScore(0.6);

        when(sectorClassifier.classify(msg)).thenReturn(Collections.singletonList(techMapping));

        // Act
        sectorGrouper.handleSourceRaw(event);

        // Assert
        verify(eventBus, times(1)).publish(eq("sector.grouped"), eq("technology"), any());
        verify(eventBus, times(1)).publish(eq("sector.grouped"), eq("technology"), mappingCaptor.capture());

        SourceSectorMappingMessage published = mappingCaptor.getValue();
        assertEquals(event.getId(), published.getRawEventId(),
                "rawEventId should link back to the IntegrationEvent ID");
        assertNotNull(published.getProcessingTimestamp(),
                "processingTimestamp should be set");
    }

    // ---------------------------------------------------------------
    // 2. Multiple sector matches → two sector.grouped events
    // ---------------------------------------------------------------
    @Test
    void testMultipleSectorMatchesPublishesPerSector() {
        // Arrange
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setEventId("msg-002");
        msg.setSourceName("fintech_source");

        IntegrationEvent event = new IntegrationEvent("source.raw", "msg-002", msg);

        SourceSectorMappingMessage financeMapping = new SourceSectorMappingMessage();
        financeMapping.setSourceName("fintech_source");
        financeMapping.setMatchedSectors(Collections.singletonList("finance"));
        financeMapping.setClassificationMethod("KEYWORD_MATCH");
        financeMapping.setConfidenceScore(0.5);

        SourceSectorMappingMessage techMapping = new SourceSectorMappingMessage();
        techMapping.setSourceName("fintech_source");
        techMapping.setMatchedSectors(Collections.singletonList("technology"));
        techMapping.setClassificationMethod("KEYWORD_MATCH");
        techMapping.setConfidenceScore(0.3);

        when(sectorClassifier.classify(msg)).thenReturn(Arrays.asList(financeMapping, techMapping));

        // Act
        sectorGrouper.handleSourceRaw(event);

        // Assert
        verify(eventBus, times(1)).publish(eq("sector.grouped"), eq("finance"), any());
        verify(eventBus, times(1)).publish(eq("sector.grouped"), eq("technology"), any());
        verify(eventBus, times(2)).publish(anyString(), anyString(), any());
    }

    // ---------------------------------------------------------------
    // 3. No match → zero sector.grouped events
    // ---------------------------------------------------------------
    @Test
    void testNoMatchPublishesNoEvents() {
        // Arrange
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setEventId("msg-003");
        msg.setSourceName("unknown");

        IntegrationEvent event = new IntegrationEvent("source.raw", "msg-003", msg);

        when(sectorClassifier.classify(msg)).thenReturn(Collections.<SourceSectorMappingMessage>emptyList());

        // Act
        sectorGrouper.handleSourceRaw(event);

        // Assert
        verify(eventBus, never()).publish(anyString(), anyString(), any());
    }

    // ---------------------------------------------------------------
    // 4. rawEventId linking verified
    // ---------------------------------------------------------------
    @Test
    void testRawEventIdLinksToIntegrationEvent() {
        // Arrange
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setEventId("msg-004");
        msg.setSourceName("travel_source");

        IntegrationEvent event = new IntegrationEvent("source.raw", "msg-004", msg);

        SourceSectorMappingMessage travelMapping = new SourceSectorMappingMessage();
        travelMapping.setSourceName("travel_source");
        travelMapping.setMatchedSectors(Collections.singletonList("travel"));
        travelMapping.setClassificationMethod("KEYWORD_MATCH");
        travelMapping.setConfidenceScore(0.8);

        when(sectorClassifier.classify(msg)).thenReturn(Collections.singletonList(travelMapping));

        // Act
        sectorGrouper.handleSourceRaw(event);

        // Assert
        verify(eventBus).publish(eq("sector.grouped"), eq("travel"), mappingCaptor.capture());
        SourceSectorMappingMessage published = mappingCaptor.getValue();
        assertEquals(event.getId(), published.getRawEventId(),
                "rawEventId must be set to the IntegrationEvent ID");
    }

    // ---------------------------------------------------------------
    // 5. Error isolation: one sector fails, rest continue
    // ---------------------------------------------------------------
    @Test
    void testErrorIsolationWhenOneSectorFails() {
        // Arrange
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setEventId("msg-005");
        msg.setSourceName("multi_source");

        IntegrationEvent event = new IntegrationEvent("source.raw", "msg-005", msg);

        SourceSectorMappingMessage goodMapping = new SourceSectorMappingMessage();
        goodMapping.setSourceName("multi_source");
        goodMapping.setMatchedSectors(Collections.singletonList("retail"));
        goodMapping.setClassificationMethod("KEYWORD_MATCH");
        goodMapping.setConfidenceScore(0.7);

        SourceSectorMappingMessage badMapping = new SourceSectorMappingMessage();
        badMapping.setSourceName("multi_source");
        badMapping.setMatchedSectors(Collections.singletonList("finance"));
        badMapping.setClassificationMethod("KEYWORD_MATCH");
        badMapping.setConfidenceScore(0.4);

        when(sectorClassifier.classify(msg)).thenReturn(Arrays.asList(goodMapping, badMapping));

        // Simulate failure on the finance sector publish, but success on retail
        doThrow(new RuntimeException("Simulated publish failure"))
                .doNothing()  // second call succeeds
                .when(eventBus).publish(anyString(), anyString(), any());

        // Act
        sectorGrouper.handleSourceRaw(event);

        // Assert — eventBus should have been called twice (once fails, once succeeds)
        verify(eventBus, times(2)).publish(anyString(), anyString(), any());
        // The retail event should have been published (it was the one that succeeded)
        verify(eventBus, atLeastOnce()).publish(eq("sector.grouped"), eq("retail"), any());
    }

    // ---------------------------------------------------------------
    // 6. Null payload in IntegrationEvent → no events published
    // ---------------------------------------------------------------
    @Test
    void testNullPayloadNoEvents() {
        // Arrange
        IntegrationEvent event = new IntegrationEvent("source.raw", "null-test", null);

        // Act
        sectorGrouper.handleSourceRaw(event);

        // Assert
        verify(sectorClassifier, never()).classify(any());
        verify(eventBus, never()).publish(anyString(), anyString(), any());
    }

    // ---------------------------------------------------------------
    // 7. Classification exception → gracefully handled, no events
    // ---------------------------------------------------------------
    @Test
    void testClassificationExceptionHandledGracefully() {
        // Arrange
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setEventId("msg-007");
        msg.setSourceName("crash_source");

        IntegrationEvent event = new IntegrationEvent("source.raw", "msg-007", msg);

        when(sectorClassifier.classify(msg)).thenThrow(new RuntimeException("Classification crash"));

        // Act — should not propagate exception
        sectorGrouper.handleSourceRaw(event);

        // Assert
        verify(eventBus, never()).publish(anyString(), anyString(), any());
    }

    // ---------------------------------------------------------------
    // 8. processingTimestamp is always set on published mappings
    // ---------------------------------------------------------------
    @Test
    void testProcessingTimestampIsSet() {
        // Arrange
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setEventId("msg-008");
        msg.setSourceName("timestamp_test");

        IntegrationEvent event = new IntegrationEvent("source.raw", "msg-008", msg);

        SourceSectorMappingMessage mapping = new SourceSectorMappingMessage();
        mapping.setSourceName("timestamp_test");
        mapping.setMatchedSectors(Collections.singletonList("health-wellness"));
        mapping.setClassificationMethod("KEYWORD_MATCH");
        mapping.setConfidenceScore(0.9);

        when(sectorClassifier.classify(msg)).thenReturn(Collections.singletonList(mapping));

        // Act
        sectorGrouper.handleSourceRaw(event);

        // Assert
        verify(eventBus).publish(eq("sector.grouped"), eq("health-wellness"), mappingCaptor.capture());
        assertNotNull(mappingCaptor.getValue().getProcessingTimestamp(),
                "processingTimestamp must be set before publishing");
    }
}
