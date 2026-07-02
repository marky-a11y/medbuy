package com.autoresolve.mediabuying.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Login page
        registry.addViewController("/login").setViewName("login");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origins = allowedOrigins;
        if (origins == null || origins.trim().isEmpty()) {
            // Default to same-origin only (no CORS)
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
