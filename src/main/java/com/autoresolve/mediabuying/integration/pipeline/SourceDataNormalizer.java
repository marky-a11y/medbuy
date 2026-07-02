package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.integration.dto.RawSourceData;
import com.autoresolve.mediabuying.messaging.dto.NormalizedSourceMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stage 1 normalizer that converts a {@link RawSourceData} (output of any
 * DSRC-03 wrapper) into a {@link NormalizedSourceMessage} for publishing
 * on the event bus.
 * <p>
 * This is a pure transformation — no side effects, no I/O. The returned
 * message carries a freshly generated {@code eventId} and copies all
 * relevant fields from the raw DTO.
 * </p>
 */
@Component
public class SourceDataNormalizer {

    private static final Logger log = LoggerFactory.getLogger(SourceDataNormalizer.class);

    /**
     * Normalize a single {@link RawSourceData} into a {@link NormalizedSourceMessage}.
     *
     * @param rawData the raw source data from a wrapper; may be {@code null}
     * @return a fully populated {@link NormalizedSourceMessage}, or {@code null}
     *         when the input is {@code null}
     */
    public NormalizedSourceMessage normalize(RawSourceData rawData) {
        if (rawData == null) {
            log.warn("Received null RawSourceData — returning null NormalizedSourceMessage");
            return null;
        }

        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setEventId(UUID.randomUUID().toString());
        msg.setSourceName(rawData.getSourceName());
        msg.setSourceUrl(rawData.getSourceUrl());
        msg.setSourceType(rawData.getSourceType());
        msg.setRawData(rawData.getRawPayload());
        msg.setNormalizedSummary(rawData.getNormalizedSummary());
        msg.setIngestionTimestamp(rawData.getFetchTimestamp());
        msg.setIngestionKey(rawData.getIngestionKey());

        log.trace("Normalized RawSourceData sourceName={} → eventId={}", rawData.getSourceName(), msg.getEventId());
        return msg;
    }
}
