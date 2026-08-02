package com.smartwork.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "smartwork.security")
public record SecurityProperties(Jwt jwt, Cors cors) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl,
                      Duration refreshTokenTtl, Duration clockSkew) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
