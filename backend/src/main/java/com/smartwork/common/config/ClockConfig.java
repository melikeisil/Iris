package com.smartwork.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injected clock. Nothing in the codebase calls {@code Instant.now()} directly,
 * so SLA windows, token expiry and workload periods are all testable without sleeping.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
