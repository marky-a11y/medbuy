package com.autoresolve.mediabuying.messaging.producer;

import com.autoresolve.mediabuying.eventbus.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Produces on-demand data refresh commands to the {@code data-refresh.internal}
 * event bus topic.
 */
@Component
public class DataRefreshProducer {

    private static final Logger log = LoggerFactory.getLogger(DataRefreshProducer.class);

    private final EventBus eventBus;

    public DataRefreshProducer(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void sendDataRefreshCommand() {
        String message = "{\"command\":\"REFRESH_ALL\",\"timestamp\":\"" + java.time.Instant.now() + "\"}";
        eventBus.publish("data-refresh.internal", "refresh-all", message);
        log.debug("Data refresh command sent to data-refresh.internal");
    }
}
