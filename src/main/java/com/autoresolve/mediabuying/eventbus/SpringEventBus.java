package com.autoresolve.mediabuying.eventbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link EventBus} that publishes events
 * in-JVM via Spring's {@link ApplicationEventPublisher}.
 *
 * <p>No external broker required — perfect for local development on Windows.
 * Asynchronous processing is achieved by adding {@code @Async} on consumer
 * {@code @EventListener} methods.
 */
@Component
public class SpringEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(SpringEventBus.class);

    private final ApplicationEventPublisher publisher;

    public SpringEventBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(String topic, String key, Object event) {
        IntegrationEvent integrationEvent = new IntegrationEvent(topic, key, event);
        log.trace("Publishing event to topic='{}' key='{}' type='{}'",
                topic, key, event.getClass().getSimpleName());
        publisher.publishEvent(integrationEvent);
    }
}
