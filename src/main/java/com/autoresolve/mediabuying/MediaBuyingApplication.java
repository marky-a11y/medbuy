package com.autoresolve.mediabuying;

import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * Tracks the lifecycle phase so the shutdown hook can distinguish between
     * an early crash (phase != READY) and an external kill while running
     * (phase == READY). Updated at each milestone in the startup sequence.
     */
    private static final AtomicReference<String> lifecyclePhase =
            new AtomicReference<>("INITIAL");

    public static void main(String[] args) {

        lifecyclePhase.set("STARTING");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            String phase = lifecyclePhase.get();
            if ("FAILED".equals(phase) || "STARTING".equals(phase) || "INITIALIZING".equals(phase)) {
                log.error("******** JVM SHUTDOWN HOOK EXECUTED (lifecycle: {}, likely startup failure) ********", phase);
                System.err.println("******** JVM SHUTDOWN (lifecycle: " + phase + ") ********");
            } else {
                log.warn("******** JVM SHUTDOWN HOOK EXECUTED (lifecycle: {}, likely external kill / Railway restart) ********", phase);
                System.err.println("******** JVM SHUTDOWN (lifecycle: " + phase + ") ********");
            }
        }));

        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
            log.error("UNCAUGHT EXCEPTION", e));

        log.info("Starting Media Buying Dashboard (PORT env = {})", System.getenv("PORT"));

        // Set the port from the PORT env var as a system property so it takes
        // precedence over both application.yml AND any SERVER_PORT env var
        // (Spring Boot's relaxed binding maps SERVER_PORT → server.port,
        // which would otherwise override YAML). System properties sit above
        // OS environment variables in Spring's property source order.
        String port = System.getenv("PORT");
        if (port != null && !port.isEmpty()) {
            log.info("Setting server.port to {} from PORT env var", port);
            System.setProperty("server.port", port);
        }

        // Register the failure listener BEFORE Spring starts, so it fires even
        // if context initialization fails early (a @Bean listener won't be created
        // before the failure).
        SpringApplication app = new SpringApplication(MediaBuyingApplication.class);
        app.addListeners((ApplicationListener<ApplicationFailedEvent>) event -> {
            lifecyclePhase.set("FAILED");
            log.error("=== ApplicationFailedEvent ===", event.getException());
            // Also dump to stderr in case logging subsystem is already shut down
            System.err.println("=== ApplicationFailedEvent ===");
            if (event.getException() != null) {
                event.getException().printStackTrace(System.err);
            }
        });

        lifecyclePhase.set("INITIALIZING");
        app.run(args);
    }

    @Bean
    ApplicationRunner portLogger(Environment env) {
        return args -> {
            lifecyclePhase.set("READY");
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
