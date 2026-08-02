/**
 * Authentication: login, JWT issuance, refresh-token rotation with reuse detection,
 * password hashing and the HTTP security configuration.
 *
 * <p>Authorization decisions live in {@code policy} and in each module's access evaluator;
 * this module only establishes who the caller is.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Auth",
        allowedDependencies = {"common", "identity::application",
                "team::application", "organization::application"})
package com.smartwork.auth;
