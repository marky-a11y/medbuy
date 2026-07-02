package com.autoresolve.mediabuying;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class MediaBuyingApplication {

    public static void main(String[] args) {
        System.out.println("RAILWAY EXPECTED PORT = \"" + System.getenv("PORT") + "\"");
        SpringApplication.run(MediaBuyingApplication.class, args);
    }

    @Bean
    ApplicationRunner portLogger(Environment env) {
        return args -> {
            String resolvedPort = env.getProperty("server.port");
            String[] activeProfiles = env.getActiveProfiles();
            String portEnv = System.getenv("PORT");
            String portProperty = System.getProperty("server.port");
            System.out.println("=== PORT DEBUG ===");
            System.out.println("  RAILWAY PORT env var  = \"" + portEnv + "\"");
            System.out.println("  System property (-D) = \"" + portProperty + "\"");
            System.out.println("  Active profiles       = " + java.util.Arrays.toString(activeProfiles));
            System.out.println("  Resolved server.port  = \"" + resolvedPort + "\"");
            System.out.println("=== END PORT DEBUG ===");
        };
    }
}
