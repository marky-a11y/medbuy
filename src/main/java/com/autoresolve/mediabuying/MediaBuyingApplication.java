package com.autoresolve.mediabuying;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class MediaBuyingApplication {

    private static final Logger log = LoggerFactory.getLogger(MediaBuyingApplication.class);

    public static void main(String[] args) {

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            log.error("******** JVM SHUTDOWN HOOK EXECUTED ********")
        ));

        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
            log.error("UNCAUGHT EXCEPTION", e));

        log.info("Starting Media Buying Dashboard (PORT env = {})", System.getenv("PORT"));

        // Register the failure listener BEFORE Spring starts, so it fires even
        // if context initialization fails early (a @Bean listener won't be created
        // before the failure).
        SpringApplication app = new SpringApplication(MediaBuyingApplication.class);
        app.addListeners((ApplicationListener<ApplicationFailedEvent>) event -> {
            log.error("=== ApplicationFailedEvent ===", event.getException());
            // Also dump to stderr in case logging subsystem is already shut down
            System.err.println("=== ApplicationFailedEvent ===");
            if (event.getException() != null) {
                event.getException().printStackTrace(System.err);
            }
        });

        app.run(args);
    }

    @Bean
    ApplicationRunner portLogger(Environment env) {
        return args -> {
            log.info("Resolved server.port = {}, active profiles = {}, PORT env = {}",
                    env.getProperty("server.port"),
                    java.util.Arrays.toString(env.getActiveProfiles()),
                    System.getenv("PORT"));
        };
    }

    @Bean
    ApplicationListener<ApplicationStartedEvent> started() {
        return event -> log.info("=== ApplicationStartedEvent ===");
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> ready() {
        return event -> log.info("=== ApplicationReadyEvent ===");
    }

}
