package com.smartwork.analysis.domain;

import com.smartwork.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A stored AI proposal plus its provenance.
 *
 * <p>The field worth explaining is {@code sourceUpdatedAt}: the work item's
 * {@code updated_at} captured at generation time. A reviewer approving a recommendation is
 * approving it <em>against the item as it read when the analysis ran</em>. If someone has
 * edited the item since, applying the recommendation would silently overwrite changes the
 * reviewer never saw. {@link #isStaleAgainst} makes that detectable instead of a race.
 */
@Entity
@Table(name = "recommendations")
public class Recommendation extends TenantEntity {

    public enum Type {
        ANALYSIS, PLAN, ASSIGNMENT
    }

    public enum Status {
        PENDING, AWAITING_APPROVAL, APPROVED, REJECTED, APPLIED, STALE, SUPERSEDED, FAILED
    }

    @Column(name = "work_item_id", nullable = false, updatable = false)
    private UUID workItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_type", nullable = false, updatable = false, length = 16)
    private Type recommendationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private Status status = Status.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_payload")
    private Map<String, Object> analysisPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggested_plan")
    private Map<String, Object> suggestedPlan;

    /**
     * Answers to this analysis's {@code clarification_questions}, submitted through
     * {@code POST /work-items/{id}/additional-information}. Each entry carries
     * {@code question_id}, {@code field} and {@code answer} — the same shape the AI
     * service expects back on the next analysis request.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clarification_answers")
    private List<Map<String, Object>> clarificationAnswers;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "routing_decision", length = 32)
    private String routingDecision;

    @Column(name = "routing_reason_codes", length = 512)
    private String routingReasonCodes;

    @Column(name = "llm_provider", length = 32)
    private String llmProvider;

    @Column(name = "llm_model", length = 64)
    private String llmModel;

    @Column(name = "llm_latency_ms")
    private Integer llmLatencyMs;

    @Column(name = "prompt_version", length = 16)
    private String promptVersion;

    @Column(name = "is_mock", nullable = false)
    private boolean mock;

    @Column(name = "assignment_policy_id")
    private UUID assignmentPolicyId;

    @Column(name = "assignment_policy_version")
    private Long assignmentPolicyVersion;

    @Column(name = "policy_engine_version", nullable = false)
    private short policyEngineVersion = 1;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "error_detail", columnDefinition = "text")
    private String errorDetail;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Recommendation() {
    }

    public Recommendation(UUID organizationId, UUID workItemId, Type recommendationType) {
        super(organizationId);
        this.workItemId = workItemId;
        this.recommendationType = recommendationType;
    }

    /** The work item has moved on since this analysis ran; applying it would overwrite edits. */
    public boolean isStaleAgainst(Instant currentSourceUpdatedAt) {
        return sourceUpdatedAt != null
                && currentSourceUpdatedAt != null
                && currentSourceUpdatedAt.isAfter(sourceUpdatedAt);
    }

    public void markFailed(String detail) {
        this.status = Status.FAILED;
        // The DB check constraint requires a detail on FAILED; defaulting here rather than
        // letting a constraint violation surface as an opaque 500.
        this.errorDetail = (detail == null || detail.isBlank()) ? "Analysis failed without detail" : detail;
    }

    public void markStale() {
        this.status = Status.STALE;
    }

    public void supersededBy(UUID newer) {
        this.status = Status.SUPERSEDED;
        this.supersededBy = newer;
    }

    public void markApplied() {
        this.status = Status.APPLIED;
    }

    public void recordProvenance(
            String provider, String model, Integer latencyMs, String promptVersion, boolean isMock) {
        this.llmProvider = provider;
        this.llmModel = model;
        this.llmLatencyMs = latencyMs;
        this.promptVersion = promptVersion;
        this.mock = isMock;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public Type getRecommendationType() {
        return recommendationType;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Map<String, Object> getAnalysisPayload() {
        return analysisPayload;
    }

    public void setAnalysisPayload(Map<String, Object> analysisPayload) {
        this.analysisPayload = analysisPayload;
    }

    public Map<String, Object> getSuggestedPlan() {
        return suggestedPlan;
    }

    public void setSuggestedPlan(Map<String, Object> suggestedPlan) {
        this.suggestedPlan = suggestedPlan;
    }

    public List<Map<String, Object>> getClarificationAnswers() {
        return clarificationAnswers;
    }

    public void recordClarificationAnswers(List<Map<String, Object>> answers) {
        this.clarificationAnswers = answers;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public void setSourceUpdatedAt(Instant sourceUpdatedAt) {
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public Instant getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public Integer getLlmLatencyMs() {
        return llmLatencyMs;
    }

    public boolean isMock() {
        return mock;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public String getRoutingDecision() {
        return routingDecision;
    }

    /** Written by the policy engine in Phase 6. */
    public void recordRouting(String route, String reasonCodesCsv) {
        this.routingDecision = route;
        this.routingReasonCodes = reasonCodesCsv;
    }

    /**
     * Pins which policy and which engine version produced the routing decision.
     *
     * <p>Without these, "replayable against the policy that was actually in force"
     * (docs/08 §8) is not achievable: {@code assignment_policies} is mutable, so a replay
     * would silently use today's thresholds against a year-old decision and conclude the
     * engine had changed its mind. A null {@code policyId} means built-in defaults.
     */
    public void recordPolicy(UUID policyId, Long policyVersion, int engineVersion) {
        this.assignmentPolicyId = policyId;
        this.assignmentPolicyVersion = policyVersion;
        this.policyEngineVersion = (short) engineVersion;
    }

    public UUID getAssignmentPolicyId() {
        return assignmentPolicyId;
    }

    public Long getAssignmentPolicyVersion() {
        return assignmentPolicyVersion;
    }

    public short getPolicyEngineVersion() {
        return policyEngineVersion;
    }

    public String getRoutingReasonCodes() {
        return routingReasonCodes;
    }
}
