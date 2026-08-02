package com.smartwork.report.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Binds the same {@code smartwork.ai.*} keys {@code analysis}'s {@code AiServiceProperties}
 * binds, independently — the {@code report} module deliberately depends on nothing but
 * {@code common} (see its {@code package-info}), so it owns a second, small properties
 * class rather than reaching into {@code analysis.infrastructure} for one. Both classes
 * read the same environment variables and always agree in practice; there is exactly one
 * AI service, one base URL, one service token.
 */
@ConfigurationProperties(prefix = "smartwork.ai")
public class ReportAiProperties {

    private String baseUrl = "http://ai-service:8000";
    private String serviceToken = "";
    private Duration timeout = Duration.ofSeconds(50);
    private boolean enabled = true;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
