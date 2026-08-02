package com.smartwork.analysis.infrastructure;

import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalysisEnvelope;
import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalysisValidationError;
import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalyzeDevelopmentRequest;
import com.smartwork.analysis.infrastructure.AiServiceDtos.AnalyzeTicketRequest;
import com.smartwork.analysis.infrastructure.AiServiceDtos.DecomposeEnvelope;
import com.smartwork.analysis.infrastructure.AiServiceDtos.DecomposeRequest;
import com.smartwork.analysis.infrastructure.AiServiceDtos.ExtractSkillsEnvelope;
import com.smartwork.analysis.infrastructure.AiServiceDtos.ExtractSkillsRequest;
import com.smartwork.common.web.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * HTTP client for the FastAPI AI service.
 *
 * <p>Every method returns {@link Outcome} rather than throwing on failure, and that is the
 * central design decision here. docs/00 promises the platform runs fully with no AI
 * provider; if an unreachable AI container could propagate an exception up through
 * {@code WorkItemService.create}, filing a ticket would fail because the analysis service
 * was down. Analysis is an enhancement to a work item, not a precondition for one.
 *
 * <p>The three outcomes are kept distinct because they mean different things to the policy
 * engine in Phase 6: {@code UNAVAILABLE} is retryable and the item should be re-analyzed
 * later; {@code REJECTED} means the model produced unusable output twice and a human must
 * look (docs/07 §8 routes this to MANUAL_EXCEPTION_REVIEW); {@code DISABLED} is a
 * deliberate configuration and should not raise an alert at all.
 */
@Component
@EnableConfigurationProperties(AiServiceProperties.class)
public class AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisClient.class);
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final AiServiceProperties properties;

    public AiAnalysisClient(RestClient.Builder builder, AiServiceProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(SERVICE_TOKEN_HEADER, properties.getServiceToken())
                .build();
    }

    public enum Status {
        OK,
        /** Network failure, timeout, or 5xx. Retryable. */
        UNAVAILABLE,
        /** The AI service returned 422 — validated twice and still unusable. */
        REJECTED,
        /** Deliberately switched off; not an error. */
        DISABLED
    }

    public record Outcome<T>(Status status, T body, AnalysisValidationError validationError, String detail) {

        public boolean isOk() {
            return status == Status.OK;
        }

        public Optional<T> value() {
            return Optional.ofNullable(body);
        }

        static <T> Outcome<T> ok(T body) {
            return new Outcome<>(Status.OK, body, null, null);
        }

        static <T> Outcome<T> unavailable(String detail) {
            return new Outcome<>(Status.UNAVAILABLE, null, null, detail);
        }

        static <T> Outcome<T> rejected(AnalysisValidationError error) {
            return new Outcome<>(Status.REJECTED, null, error, "AI output failed validation twice");
        }

        static <T> Outcome<T> disabled() {
            return new Outcome<>(Status.DISABLED, null, null, "AI analysis is disabled by configuration");
        }
    }

    public Outcome<AnalysisEnvelope> analyzeTicket(AnalyzeTicketRequest request) {
        return post("/internal/v1/analyze/ticket", request, AnalysisEnvelope.class);
    }

    public Outcome<AnalysisEnvelope> analyzeDevelopment(AnalyzeDevelopmentRequest request) {
        return post("/internal/v1/analyze/development", request, AnalysisEnvelope.class);
    }

    public Outcome<DecomposeEnvelope> decompose(DecomposeRequest request) {
        return post("/internal/v1/decompose", request, DecomposeEnvelope.class);
    }

    public Outcome<ExtractSkillsEnvelope> extractSkills(ExtractSkillsRequest request) {
        return post("/internal/v1/extract-skills", request, ExtractSkillsEnvelope.class);
    }

    /** Which provider the AI service selected, for the UI's "demo analysis" badge. */
    public Outcome<ProviderInfo> providerInfo() {
        if (!properties.isEnabled()) {
            return Outcome.disabled();
        }
        try {
            ProviderInfo info = restClient.get()
                    .uri("/internal/v1/provider")
                    .retrieve()
                    .body(ProviderInfo.class);
            return Outcome.ok(info);
        } catch (Exception e) {
            return Outcome.unavailable(e.getMessage());
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ProviderInfo(
            @com.fasterxml.jackson.annotation.JsonProperty("provider") String provider,
            @com.fasterxml.jackson.annotation.JsonProperty("model") String model,
            @com.fasterxml.jackson.annotation.JsonProperty("is_mock") boolean isMock,
            @com.fasterxml.jackson.annotation.JsonProperty("prompt_version") String promptVersion) {
    }

    private <T> Outcome<T> post(String path, Object request, Class<T> responseType) {
        if (!properties.isEnabled()) {
            return Outcome.disabled();
        }
        try {
            // exchange() rather than retrieve(): a 422 carries a structured body we want to
            // decode into AnalysisValidationError, and retrieve()'s status handlers cannot
            // change the return type. Swallowing the 422 and then parsing its body as the
            // success type would silently produce an all-null "successful" analysis.
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CorrelationId.HEADER, CorrelationId.current())
                    .body(request)
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        if (res.getStatusCode().is2xxSuccessful()) {
                            return Outcome.ok(res.bodyTo(responseType));
                        }
                        if (status == 422) {
                            AnalysisValidationError error = res.bodyTo(AnalysisValidationError.class);
                            log.warn("AI service rejected its own output for {} after {} attempts: {}",
                                    path,
                                    error == null ? "?" : error.attempts(),
                                    error == null ? "no detail" : error.errors());
                            return Outcome.<T>rejected(error);
                        }
                        log.warn("AI service returned {} for {}", status, path);
                        return Outcome.<T>unavailable("AI service returned HTTP " + status);
                    }, false);
        } catch (ResourceAccessException e) {
            log.warn("AI service unreachable at {}{}: {}", properties.getBaseUrl(), path, e.getMessage());
            return Outcome.unavailable("AI service unreachable: " + e.getMessage());
        } catch (Exception e) {
            log.warn("AI service call to {} failed: {}", path, e.getMessage());
            return Outcome.unavailable(e.getMessage());
        }
    }
}
