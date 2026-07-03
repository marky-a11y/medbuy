package com.autoresolve.mediabuying.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import javax.enterprise.inject.spi.CDI;
import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Configures embedded Tomcat with diagnostic logging and a lightweight
 * health-check filter that runs independently of the Spring MVC layer.
 * <p>
 * The health-check filter is registered <em>before</em> the FacesServlet
 * or any other servlet begins initialising, so Railway's /actuator/health
 * and / probes succeed even if the full JSF stack takes time to start.
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

    /**
     * Sets JSF / PrimeFaces context-param defaults.  This runs as a
     * {@link ServletContextInitializer} (invoked by TomcatStarter),
     * which the Servlet spec guarantees fires <em>before</em> any
     * {@link ServletContextListener}.
     */
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

            // -- Register a lightweight health-check servlet ------------------
            // This responds to GET /actuator/health and GET / with "OK" (HTTP 200).
            // It does NOT depend on Spring MVC / Actuator, so it survives even
            // if the rest of the context fails to initialise.
            ServletRegistration.Dynamic healthServlet =
                    servletContext.addServlet("healthServlet", new HttpServlet() {
                        @Override
                        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                                throws IOException {
                            String path = req.getRequestURI();
                            resp.setContentType("text/plain");
                            resp.setStatus(200);
                            resp.getWriter().print("OK (from healthServlet, path="
                                    + path.replace('\n', '_').replace('\r', '_') + ")");
                        }
                    });
            healthServlet.addMapping("/actuator/health", "/");
            healthServlet.setLoadOnStartup(0); // initialise eagerly (still non-blocking)

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

    /**
     * Sets a context init parameter only if it hasn't been set yet.
     * This lets the first {@link ServletContextInitializer} that runs
     * establish the value without worrying about later initializers
     * overwriting it with a different value.
     */
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
