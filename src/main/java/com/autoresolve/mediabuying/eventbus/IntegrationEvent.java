package com.autoresolve.mediabuying.eventbus;

import java.time.Instant;
import java.util.UUID;

/**
 * Wrapper event that carries topic metadata alongside the payload,
 * enabling {@link org.springframework.context.event.EventListener} methods
 * to filter on topic via SpEL conditions.
 */
public class IntegrationEvent {

    private final String id;
    private final String topic;
    private final String key;
    private final Object payload;
    private final Instant timestamp;

    public IntegrationEvent(String topic, String key, Object payload) {
        this.id = UUID.randomUUID().toString();
        this.topic = topic;
        this.key = key;
        this.payload = payload;
        this.timestamp = Instant.now();
    }

    public String getId()               { return id; }
    public String getTopic()            { return topic; }
    public String getKey()              { return key; }
    public Object getPayload()          { return payload; }
    public Instant getTimestamp()       { return timestamp; }

    @Override
    public String toString() {
        return "IntegrationEvent{id='" + id + "', topic='" + topic + "'}";
    }
}
