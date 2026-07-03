package com.autoresolve.mediabuying.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import javax.enterprise.inject.spi.CDI;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Configures JSF / PrimeFaces context-param defaults and registers a
 * diagnostic {@link ServletContextListener}.
 * <p>
 * Health-check endpoints ({@code /actuator/health} and {@code /}) are
 * handled by the Spring Boot Actuator and {@link com.autoresolve.mediabuying.controller.HealthDebugController}
 * respectively, both of which run inside the Spring {@code DispatcherServlet}.
 * No raw-servlet fallback is needed because the DispatcherServlet is
 * the first servlet loaded (load-on-startup = -1 is fine for our use case).
 */
@Configuration
public class WebConfig {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    /** Tracks whether at least one ServletContextInitializer has been invoked. */
    private static final AtomicBoolean initializerInvoked = new AtomicBoolean(false);

    // ------------------------------------------------------------------
    // ServletContextInitializer — runs *before* ServletContextListeners
    // per the Servlet 3.0 spec.
    // ------------------------------------------------------------------

    @Bean
    @Order(0)
    public ServletContextInitializer servletContextCustomizer() {
        return servletContext -> {
            initializerInvoked.set(true);
            log.info("=== WebConfig ServletContextInitializer.onStartup() ===");

            // -- Set required Mojarra / PrimeFaces context params -------------
            setInitParamIfAbsent(servletContext, "javax.faces.PROJECT_STAGE", "Development");
            setInitParamIfAbsent(servletContext, "primefaces.THEME", "none");
            setInitParamIfAbsent(servletContext, "primefaces.FONT_AWESOME", "true");
            setInitParamIfAbsent(servletContext, "primefaces.SECRET",
                    Long.toHexString(System.nanoTime()));

            // -- Log all registered servlets -----------------------------------
            log.info("Registered servlets:");
            servletContext.getServletRegistrations().forEach((name, reg) -> {
                log.info("  Servlet: {} -> {}", name,
                        String.join(", ", reg.getMappings()));
                reg.getInitParameters().forEach((k, v) ->
                        log.info("    InitParam: {} = {}", k, v));
            });

            // -- Log all context init parameters ------------------------------
            log.info("ServletContext init parameters:");
            Enumeration<String> paramNames = servletContext.getInitParameterNames();
            while (paramNames.hasMoreElements()) {
                String name = paramNames.nextElement();
                log.info("  {} = {}", name, servletContext.getInitParameter(name));
            }

            log.info("=== WebConfig ServletContextInitializer.onStartup() done ===");
        };
    }

    // ------------------------------------------------------------------
    // ServletContextListener — runs *after* ServletContextInitializers
    // per the Servlet 3.0 spec.
    // ------------------------------------------------------------------

    @Bean
    @Order(0)
    public ServletListenerRegistrationBean<ServletContextListener> diagnosticListener() {
        ServletListenerRegistrationBean<ServletContextListener> bean =
                new ServletListenerRegistrationBean<>();
        bean.setListener(new ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent sce) {
                ServletContext ctx = sce.getServletContext();
                log.info("=== DiagnosticListener.contextInitialized() ===");

                // Verify the initializer ran
                log.info("  initializerInvoked = {}", initializerInvoked.get());

                // Log all context init parameters
                log.info("Context init parameters from ServletContext:");
                Enumeration<String> names = ctx.getInitParameterNames();
                while (names.hasMoreElements()) {
                    String name = names.nextElement();
                    log.info("  {} = {}", name, ctx.getInitParameter(name));
                }

                // Check CDI availability
                try {
                    CDI.current();
                    log.info("CDI.current() succeeded - BeanManager is available");
                } catch (Exception e) {
                    log.info("CDI BeanManager not present - this is expected; JSF 2.3 runs without CDI when faces-config.xml < 2.3 and web.xml < 4.0");
                }

                log.info("=== DiagnosticListener.contextInitialized() done ===");
            }

            @Override
            public void contextDestroyed(ServletContextEvent sce) {
                log.info("=== DiagnosticListener.contextDestroyed() ===");
            }
        });
        // Order 0 = highest priority (runs first among listeners, but
        // always after ServletContextInitializers).
        bean.setOrder(0);
        return bean;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void setInitParamIfAbsent(ServletContext ctx, String name, String value) {
        String existing = ctx.getInitParameter(name);
        if (existing == null) {
            ctx.setInitParameter(name, value);
            log.debug("  Set init param {} = {}", name, value);
        } else {
            log.debug("  Init param {} already set to {}, skipping", name, existing);
        }
    }
}
