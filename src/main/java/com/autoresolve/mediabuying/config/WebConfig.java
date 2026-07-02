package com.autoresolve.mediabuying.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.enterprise.inject.spi.CDI;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;

/**
 * Diagnoses and fixes CDI/JSF configuration issues in embedded Tomcat.
 * Logs all context init parameters and CDI availability status.
 */
@Configuration
public class WebConfig {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    @Bean
    public ServletContextInitializer servletContextCustomizer() {
        return new ServletContextInitializer() {
            @Override
            public void onStartup(ServletContext servletContext) {
                log.info("=== WebConfig ServletContextInitializer.onStartup() ===");
                
                // Set ALL required Mojarra/PrimeFaces context parameters
                
                servletContext.setInitParameter("javax.faces.PROJECT_STAGE", "Development");
                servletContext.setInitParameter("primefaces.THEME", "none");
                servletContext.setInitParameter("primefaces.FONT_AWESOME", "true");
                
                // Log all registered servlets and their init parameters
                log.info("Registered servlets:");
                for (ServletRegistration registration : servletContext.getServletRegistrations().values()) {
                    log.info("  Servlet: {} -> mappings: {}", registration.getName(), 
                             String.join(", ", registration.getMappings()));
                    for (String paramName : registration.getInitParameters().keySet()) {
                        log.info("    InitParam: {} = {}", paramName, registration.getInitParameters().get(paramName));
                    }
                }
                
                // Log ALL context init parameters
                log.info("ServletContext init parameters:");
                java.util.Enumeration<String> paramNames = servletContext.getInitParameterNames();
                while (paramNames.hasMoreElements()) {
                    String name = paramNames.nextElement();
                    log.info("  {} = {}", name, servletContext.getInitParameter(name));
                }
            }
        };
    }

    /**
     * Diagnostic servlet context listener that logs CDI status and init parameters
     * at different lifecycle phases.
     */
    @Bean
    public ServletListenerRegistrationBean<ServletContextListener> diagnosticListener() {
        ServletListenerRegistrationBean<ServletContextListener> bean = 
                new ServletListenerRegistrationBean<>();
        bean.setListener(new ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent sce) {
                ServletContext ctx = sce.getServletContext();
                log.info("=== DiagnosticListener.contextInitialized() ===");
                
                // Log all context init parameters
                log.info("Context init parameters from ServletContext:");
                java.util.Enumeration<String> names = ctx.getInitParameterNames();
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
            }

            @Override
            public void contextDestroyed(ServletContextEvent sce) {
                log.info("=== DiagnosticListener.contextDestroyed() ===");
            }
        });
        bean.setOrder(0); // Run first
        return bean;
    }
}
