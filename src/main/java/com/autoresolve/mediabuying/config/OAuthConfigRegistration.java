package com.autoresolve.mediabuying.config;

import com.autoresolve.mediabuying.integration.auth.OAuthTokenManager;
import com.autoresolve.mediabuying.integration.auth.OAuthTokenManager.PlatformOAuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads OAuth configuration from {@code integration.oauth.*} in application.yml
 * and registers each platform with the {@link OAuthTokenManager}.
 */
@Configuration
@ConfigurationProperties(prefix = "integration.oauth")
public class OAuthConfigRegistration {

    private static final Logger log = LoggerFactory.getLogger(OAuthConfigRegistration.class);

    private final OAuthTokenManager oAuthTokenManager;

    /** Map of platform -> platform-specific OAuth properties. */
    private Map<String, PlatformOAuthProperties> platforms = new HashMap<>();

    public OAuthConfigRegistration(OAuthTokenManager oAuthTokenManager) {
        this.oAuthTokenManager = oAuthTokenManager;
    }

    @PostConstruct
    public void registerPlatforms() {
        for (Map.Entry<String, PlatformOAuthProperties> entry : platforms.entrySet()) {
            String platformKey = entry.getKey();
            PlatformOAuthProperties props = entry.getValue();

            PlatformOAuthConfig config = new PlatformOAuthConfig();
            config.setClientId(props.getClientId());
            config.setClientSecret(props.getClientSecret());
            config.setRefreshToken(props.getRefreshToken());
            config.setAccessToken(props.getAccessToken());
            config.setDeveloperToken(props.getDeveloperToken());
            config.setTokenUrl(props.getTokenUrl());
            config.setScope(props.getScope());

            oAuthTokenManager.registerPlatform(platformKey, config);
            log.debug("Registered OAuth config for platform: {}", platformKey);
        }
        log.info("Registered {} OAuth platform configuration(s)", platforms.size());
    }

    public Map<String, PlatformOAuthProperties> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(Map<String, PlatformOAuthProperties> platforms) {
        this.platforms = platforms;
    }

    /**
     * Nested properties for a single platform's OAuth configuration.
     */
    public static class PlatformOAuthProperties {
        private String clientId;
        private String clientSecret;
        private String refreshToken;
        private String accessToken;
        private String developerToken;
        private String tokenUrl;
        private String scope;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public String getDeveloperToken() { return developerToken; }
        public void setDeveloperToken(String developerToken) { this.developerToken = developerToken; }

        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
    }
}
