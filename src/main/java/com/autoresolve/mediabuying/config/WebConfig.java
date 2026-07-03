package com.autoresolve.mediabuying.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import javax.servlet.ServletContext;

/**
 * Configures JSF / PrimeFaces context-param defaults.
 */
@Configuration
public class WebConfig {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    @Bean
    @Order(0)
    public ServletContextInitializer servletContextCustomizer() {
        return servletContext -> {
            setInitParamIfAbsent(servletContext, "javax.faces.PROJECT_STAGE", "Production");
            setInitParamIfAbsent(servletContext, "primefaces.THEME", "none");
            setInitParamIfAbsent(servletContext, "primefaces.FONT_AWESOME", "true");
            setInitParamIfAbsent(servletContext, "primefaces.SECRET",
                    Long.toHexString(System.nanoTime()));
            log.info("JSF/PrimeFaces context params configured");
        };
    }

    private static void setInitParamIfAbsent(ServletContext ctx, String name, String value) {
        String existing = ctx.getInitParameter(name);
        if (existing == null) {
            ctx.setInitParameter(name, value);
        }
    }
}
