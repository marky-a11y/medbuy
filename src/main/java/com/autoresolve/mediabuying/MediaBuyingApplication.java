package com.autoresolve.mediabuying;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
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
                log.error("******** JVM SHUTDOWN HOOK (lifecycle: {}, likely startup failure) ********", phase);
                System.err.println("******** JVM SHUTDOWN (lifecycle: " + phase + ") ********");
            } else {
                log.warn("******** JVM SHUTDOWN HOOK (lifecycle: {}, likely external kill / Railway restart) ********", phase);
                System.err.println("******** JVM SHUTDOWN (lifecycle: " + phase + ") ********");
            }
        }));

        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
            log.error("UNCAUGHT EXCEPTION", e));

        log.info("=== MediaBuyingApplication.main() reached at {} ===", System.currentTimeMillis());
        log.info("Starting Media Buying Dashboard (PORT env = {}, profiles env = {}, SPRING_PROFILES_ACTIVE env = {})",
                System.getenv("PORT"), System.getenv("PROFILES_ACTIVE"), System.getenv("SPRING_PROFILES_ACTIVE"));

        // Inject the Railway PORT as a command-line argument so it has the
        // highest Spring Boot precedence, beating even SERVER_PORT env var.
        String port = System.getenv("PORT");
        if (port != null && !port.isEmpty()) {
            args = Arrays.copyOf(args, args.length + 1);
            args[args.length - 1] = "--server.port=" + port;
            log.info("Injected --server.port={} from PORT env var into runtime args", port);
        }

        // Register lifecycle listeners BEFORE Spring starts, so they fire even
        // if context initialization fails early (a @Bean listener won't be created
        // before the failure).
        SpringApplication app = new SpringApplication(MediaBuyingApplication.class);

        // ── Early lifecycle events (registered BEFORE app.run(), so they fire
        //    even if refresh hangs — these listeners are forwarded to the context
        //    by SpringApplication once it is created, so they also receive
        //    ContextRefreshedEvent etc.). ──
        app.addListeners(
            (ApplicationListener<ApplicationEnvironmentPreparedEvent>) event ->
                log.info("=== PHASE: ApplicationEnvironmentPreparedEvent at {} ===",
                    System.currentTimeMillis())
        );
        app.addListeners(
            (ApplicationListener<ApplicationPreparedEvent>) event ->
                log.info("=== PHASE: ApplicationPreparedEvent at {} ===",
                    System.currentTimeMillis())
        );
        app.addListeners(
            (ApplicationListener<ContextRefreshedEvent>) event ->
                log.info("=== PHASE: ContextRefreshedEvent at {} ===",
                    System.currentTimeMillis())
        );
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

        log.info("=== PHASE: about to call app.run() at {} ===", System.currentTimeMillis());
        try {
            app.run(args);
            log.info("=== PHASE: app.run() returned normally at {} ===", System.currentTimeMillis());
        } catch (RuntimeException e) {
            log.error("=== PHASE: app.run() threw exception: {}: {} ===", e.getClass().getSimpleName(), e.getMessage(), e);
            System.err.println("=== PHASE: app.run() threw exception: " + e.getClass().getSimpleName() + ": " + e.getMessage() + " ===");
            e.printStackTrace(System.err);
            throw e;
        }
    }

    @Bean
    ApplicationRunner portLogger(Environment env) {
        return args -> {
            lifecyclePhase.set("READY");
            String msg = "Resolved server.port = " + env.getProperty("server.port")
                    + ", active profiles = " + java.util.Arrays.toString(env.getActiveProfiles())
                    + ", PORT env = " + System.getenv("PORT");
            log.info("=== PHASE: ApplicationRunner: {} ===", msg);
        };
    }

    @Bean
    ApplicationListener<ApplicationStartedEvent> started() {
        return event -> {
            log.info("=== PHASE: ApplicationStartedEvent at {} ===",
                System.currentTimeMillis());
        };
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> ready() {
        return event -> {
            log.info("=== PHASE: ApplicationReadyEvent at {} ===",
                System.currentTimeMillis());
        };
    }

    // ---------------------------------------------------------------
    // BeanPostProcessor that logs the instantiation of every bean
    // (filtered to our packages and key infrastructure beans).
    // Use this on Railway to pinpoint exactly which bean hangs.
    // ---------------------------------------------------------------
    @Bean
    BeanPostProcessor beanInitLogger() {
        return new BeanPostProcessor() {

            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (isLoggedBean(beanName, bean)) {
                    log.info("=== BEAN BEFORE: {} ({}:{}) at {} ===",
                        beanName,
                        bean.getClass().getSimpleName(),
                        bean.getClass().getPackageName(),
                        System.currentTimeMillis());
                }
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (isLoggedBean(beanName, bean)) {
                    log.info("=== BEAN AFTER:  {} ({}:{}) at {} ===",
                        beanName,
                        bean.getClass().getSimpleName(),
                        bean.getClass().getPackageName(),
                        System.currentTimeMillis());
                }
                return bean;
            }

            private boolean isLoggedBean(String beanName, Object bean) {
                String pkg = bean.getClass().getPackageName();
                // Log all beans from our application packages
                if (pkg.startsWith("com.autoresolve.mediabuying")) {
                    return true;
                }
                // Log key infrastructure beans
                String[] infraPrefixes = {
                    "org.springframework.orm.jpa",
                    "org.springframework.transaction",
                    "com.zaxxer.hikari",
                    "org.hibernate",
                    "javax.persistence",
                    "org.springframework.boot.autoconfigure.jdbc",
                    "org.springframework.boot.autoconfigure.orm.jpa",
                    "org.springframework.web",
                    "org.springframework.context",
                    "org.springframework.beans",
                    "org.springframework.scheduling",
                    "org.springframework.cache",
                    "org.springframework.boot.context"
                };
                for (String prefix : infraPrefixes) {
                    if (pkg.startsWith(prefix)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

}
