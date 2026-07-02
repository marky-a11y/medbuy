package com.autoresolve.mediabuying.eventbus;

/**
 * Abstraction over event publishing for decoupled integration pipelines.
 *
 * <p>The single implementation {@link SpringEventBus} publishes events in-JVM
 * via Spring's {@code ApplicationEventPublisher}. No external message broker
 * is required.
 *
 * <p>Usage:
 * <pre>{@code
 *     @Autowired private EventBus eventBus;
 *     eventBus.publish("kpi.raw", "google_ads", rawKpiEvent);
 * }</pre>
 */
public interface EventBus {

    /**
     * Publish an event to the given topic/channel.
     *
     * @param topic   the logical channel name (e.g. {@code "kpi.raw"})
     * @param key     a partitioning key or event identifier (nullable)
     * @param event   the payload object (never null)
     */
    void publish(String topic, String key, Object event);
}
