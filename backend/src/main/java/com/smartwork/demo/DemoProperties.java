package com.smartwork.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartwork.demo")
public record DemoProperties(boolean enabled, String defaultPassword) {

    public DemoProperties {
        if (defaultPassword == null || defaultPassword.isBlank()) {
            defaultPassword = "SmartWork!Demo2026";
        }
    }
}
