package com.smartwork.auth.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Refuses to serve traffic with a missing or development-grade JWT secret outside dev and
 * test. Failing at startup is far better than discovering in production that every token
 * was signed with a secret published in the repository.
 */
@Component
public class StartupSecretValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(StartupSecretValidator.class);
    private static final Set<String> RELAXED_PROFILES = Set.of("dev", "test");
    private static final int MIN_SECRET_BYTES = 64;   // HS512 needs a 512-bit key
    private static final String DEV_SECRET_MARKER = "dev-only-secret";

    private final SecurityProperties properties;
    private final Environment environment;

    public StartupSecretValidator(SecurityProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * Runs during context initialization, not on ApplicationReadyEvent: a misconfigured
     * secret must prevent the context from coming up at all, never leave a server
     * listening with a known-weak key.
     */
    @Override
    public void afterPropertiesSet() {
        String secret = properties.jwt().secret();
        boolean relaxed = Set.of(environment.getActiveProfiles()).stream().anyMatch(RELAXED_PROFILES::contains);

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Set it in the environment before starting the backend.");
        }

        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is %d bytes; HS512 requires at least %d.".formatted(length, MIN_SECRET_BYTES));
        }

        if (secret.contains(DEV_SECRET_MARKER)) {
            if (!relaxed) {
                throw new IllegalStateException(
                        "The development JWT secret is in use outside the dev/test profiles. Refusing to start.");
            }
            log.warn("Using the shared development JWT secret. Never do this outside local development.");
        }
    }
}
