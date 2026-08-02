package com.smartwork.analysis.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "smartwork.ai")
public class AiServiceProperties {

    /** Base URL of the FastAPI service. Inside Compose this is the service name. */
    private String baseUrl = "http://ai-service:8000";

    /** Shared secret sent as {@code X-Service-Token}. Must match the AI service's. */
    private String serviceToken = "";

    /**
     * Slightly longer than the AI service's own 45s LLM timeout, so a slow-but-successful
     * model call returns a real answer rather than being cut off one second early by the
     * caller — the most annoying possible failure mode.
     */
    private Duration timeout = Duration.ofSeconds(50);

    /**
     * When false, the backend never calls the AI service and every analysis is recorded as
     * unavailable. Lets the platform run entirely without the AI container.
     */
    private boolean enabled = true;

    private int maxDecompositionItems = 12;

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

    public int getMaxDecompositionItems() {
        return maxDecompositionItems;
    }

    public void setMaxDecompositionItems(int maxDecompositionItems) {
        this.maxDecompositionItems = maxDecompositionItems;
    }
}
