package com.autoresolve.mediabuying;

import java.util.Arrays;
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

    private static final AtomicReference<String> lifecyclePhase =
            new AtomicReference<>("INITIAL");

    public static void main(String[] args) {

        lifecyclePhase.set("STARTING");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            String phase = lifecyclePhase.get();
            if ("FAILED".equals(phase) || "STARTING".equals(phase) || "INITIALIZING".equals(phase)) {
                log.error("******** JVM SHUTDOWN HOOK (lifecycle: {}, likely startup failure) ********", phase);
                System.err.println("******** JVM SHUTDOWN (lifecycle: " + phase + ") ********");
            }
        }));

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.error("UNCAUGHT EXCEPTION in thread '{}': {}", t.getName(), e.getMessage(), e);
            System.err.println("UNCAUGHT EXCEPTION in thread '" + t.getName() + "': " + e);
            e.printStackTrace(System.err);
        });

        log.info("Starting Media Buying Dashboard (PORT={})", System.getenv("PORT"));

        String port = System.getenv("PORT");
        if (port != null && !port.isEmpty()) {
            args = Arrays.copyOf(args, args.length + 1);
            args[args.length - 1] = "--server.port=" + port;
        }

        SpringApplication app = new SpringApplication(MediaBuyingApplication.class);

        app.addListeners((ApplicationListener<ApplicationFailedEvent>) event -> {
            lifecyclePhase.set("FAILED");
            log.error("=== ApplicationFailedEvent ===", event.getException());
            System.err.println("=== ApplicationFailedEvent ===");
            if (event.getException() != null) {
                event.getException().printStackTrace(System.err);
            }
        });

        lifecyclePhase.set("INITIALIZING");

        try {
            app.run(args);
            log.info("Application started successfully");
        } catch (Throwable e) {
            log.error("Application failed to start: {}", e.getMessage(), e);
            System.err.println("Application failed to start: " + e);
            e.printStackTrace(System.err);
            throw e;
        }
    }

    @Bean
    ApplicationRunner portLogger(Environment env) {
        return args -> {
            lifecyclePhase.set("READY");
            log.info("Server ready on port {}", env.getProperty("server.port"));
        };
    }

    @Bean
    ApplicationListener<ApplicationStartedEvent> started() {
        return event -> log.info("Application context started");
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> ready() {
        return event -> log.info("Application ready to serve requests");
    }

}
