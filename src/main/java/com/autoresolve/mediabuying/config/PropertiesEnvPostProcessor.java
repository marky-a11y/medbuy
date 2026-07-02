package com.autoresolve.mediabuying.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads {@code properties.env} from the classpath (and optionally from the
 * working directory) and injects all key/value pairs into the Spring
 * {@link ConfigurableEnvironment} as a {@link PropertiesPropertySource}.
 * <p>
 * This allows the {@code application.yml} placeholders such as
 * {@code ${YELP_FUSION_API_KEY:}} to resolve from the {@code properties.env}
 * file instead of relying on OS-level environment variables.
 * </p>
 * <p>
 * The property source is registered at {@link Ordered#LOWEST_PRECEDENCE} so
 * that command-line arguments and system properties still take precedence,
 * but {@code properties.env} values override OS environment variables.
 * </p>
 *
 * <h3>File locations (checked in order):</h3>
 * <ol>
 *   <li>{@code classpath:properties.env} — bundled in the JAR</li>
 *   <li>{@code file:./properties.env} — in the working directory (override)</li>
 * </ol>
 *
 * <h3>File format:</h3>
 * <pre>
 * # Comments start with #
 * KEY=VALUE
 * </pre>
 *
 * @see EnvironmentPostProcessor
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class PropertiesEnvPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(PropertiesEnvPostProcessor.class);

    /**
     * The name of the property source as it will appear in the Environment.
     */
    private static final String PROPERTY_SOURCE_NAME = "propertiesEnvFile";

    /**
     * Classpath resource name.
     */
    private static final String CLASSPATH_RESOURCE = "properties.env";

    /**
     * Working-directory resource name.
     */
    private static final String FILESYSTEM_RESOURCE = "./properties.env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        Properties props = new Properties();

        // 1) Try loading from classpath (bundled in JAR)
        Resource classpathResource = new ClassPathResource(CLASSPATH_RESOURCE);
        if (classpathResource.exists()) {
            try (InputStream is = classpathResource.getInputStream()) {
                props.load(is);
                log.debug("Loaded properties from classpath: {}", CLASSPATH_RESOURCE);
            } catch (IOException e) {
                log.warn("Failed to load {} from classpath: {}", CLASSPATH_RESOURCE, e.getMessage());
            }
        } else {
            log.debug("No classpath resource found: {}", CLASSPATH_RESOURCE);
        }

        // 2) Try loading from working directory (overrides classpath entries)
        Resource fileResource = new FileSystemResource(FILESYSTEM_RESOURCE);
        if (fileResource.exists()) {
            try (InputStream is = fileResource.getInputStream()) {
                Properties fileProps = new Properties();
                fileProps.load(is);
                // Merge: file-system entries override classpath entries
                fileProps.forEach((key, value) -> props.put(key, value));
                log.debug("Loaded/overrode properties from filesystem: {}", FILESYSTEM_RESOURCE);
            } catch (IOException e) {
                log.warn("Failed to load {} from filesystem: {}", FILESYSTEM_RESOURCE, e.getMessage());
            }
        }

        if (props.isEmpty()) {
            log.info("properties.env is empty or not found; all API keys will use their default " +
                    "(empty) values. Wrappers will fall back to mock mode.");
            return;
        }

        // 3) Register the property source at the highest precedence so it
        //    overrides OS environment variables but not command-line args or
        //    system properties.
        MutablePropertySources sources = environment.getPropertySources();
        PropertiesPropertySource propertySource = new PropertiesPropertySource(PROPERTY_SOURCE_NAME, props);

        // Add after systemProperties (which means it sits between systemProperties
        // and systemEnvironment), giving it higher precedence than OS env vars.
        if (sources.contains("systemProperties")) {
            sources.addAfter("systemProperties", propertySource);
        } else {
            // If systemProperties is missing for some reason, add first
            sources.addFirst(propertySource);
        }

        log.info("Registered {} property source with {} key(s) from properties.env",
                PROPERTY_SOURCE_NAME, props.size());
    }
}
