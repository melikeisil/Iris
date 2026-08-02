package com.smartwork.analysis.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartwork.workitem.domain.Complexity;
import com.smartwork.workitem.domain.OriginType;
import com.smartwork.workitem.domain.Priority;
import com.smartwork.workitem.domain.RiskLevel;
import com.smartwork.workitem.domain.WorkFamily;
import com.smartwork.workitem.domain.WorkItemType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wire types for the FastAPI AI service, mirroring {@code ai-service/app/schemas}.
 *
 * <p>Snake_case on the wire, camelCase in Java, mapped explicitly with
 * {@code @JsonProperty} rather than by a global naming strategy — a global strategy would
 * silently reshape every other DTO in the application to fix one integration.
 *
 * <p>Responses are annotated {@code @JsonIgnoreProperties(ignoreUnknown = true)} so the AI
 * service can add a field without breaking the backend. Requests are not: an unknown field
 * we are <em>sending</em> means the two schemas have diverged and we want to know.
 *
 * <p>Note what is absent from every response type here: no assignee, no approval, no
 * status. That is not an oversight — see docs/07 §2. The Java side could not act on such a
 * field even if the model invented one, because there is nowhere to put it.
 */
public final class AiServiceDtos {

    private AiServiceDtos() {
    }

    // ---------------- requests ----------------

    public record TeamRef(
            @JsonProperty("code") String code,
            @JsonProperty("department_code") String departmentCode) {
    }

    public record OrgContext(
            @JsonProperty("department_codes") List<String> departmentCodes,
            @JsonProperty("team_codes") List<TeamRef> teamCodes,
            @JsonProperty("project_codes") List<String> projectCodes,
            @JsonProperty("component_codes") List<String> componentCodes,
            @JsonProperty("skill_codes") List<String> skillCodes,
            @JsonProperty("process_template_codes") List<String> processTemplateCodes,
            @JsonProperty("category_codes") List<String> categoryCodes) {
    }

    public record ClarificationAnswer(
            @JsonProperty("question_id") String questionId,
            @JsonProperty("field") String field,
            @JsonProperty("answer") String answer) {
    }

    public record SimilarItem(
            @JsonProperty("reference_key") String referenceKey,
            @JsonProperty("summary") String summary,
            @JsonProperty("similarity") BigDecimal similarity) {
    }

    public record AnalyzeTicketRequest(
            @JsonProperty("correlation_id") String correlationId,
            @JsonProperty("work_item_id") String workItemId,
            @JsonProperty("summary") String summary,
            @JsonProperty("description") String description,
            @JsonProperty("reporter_department_code") String reporterDepartmentCode,
            @JsonProperty("origin_type") OriginType originType,
            @JsonProperty("priority_hint") Priority priorityHint,
            @JsonProperty("clarification_answers") List<ClarificationAnswer> clarificationAnswers,
            @JsonProperty("org_context") OrgContext orgContext,
            @JsonProperty("similar_items") List<SimilarItem> similarItems) {
    }

    public record AnalyzeDevelopmentRequest(
            @JsonProperty("correlation_id") String correlationId,
            @JsonProperty("work_item_id") String workItemId,
            @JsonProperty("work_item_type") WorkItemType workItemType,
            @JsonProperty("summary") String summary,
            @JsonProperty("description") String description,
            @JsonProperty("acceptance_criteria") String acceptanceCriteria,
            @JsonProperty("project_code") String projectCode,
            @JsonProperty("parent_summary") String parentSummary,
            @JsonProperty("existing_components") List<String> existingComponents,
            @JsonProperty("org_context") OrgContext orgContext,
            @JsonProperty("clarification_answers") List<ClarificationAnswer> clarificationAnswers) {
    }

    public record ProcessTemplateSpec(
            @JsonProperty("code") String code,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("typical_task_titles") List<String> typicalTaskTitles) {
    }

    public record DecomposeRequest(
            @JsonProperty("correlation_id") String correlationId,
            @JsonProperty("work_item_id") String workItemId,
            @JsonProperty("analysis") AnalysisResponse analysis,
            @JsonProperty("matched_template") ProcessTemplateSpec matchedTemplate,
            @JsonProperty("max_items") int maxItems,
            @JsonProperty("org_context") OrgContext orgContext) {
    }

    public record ExtractSkillsRequest(
            @JsonProperty("correlation_id") String correlationId,
            @JsonProperty("work_item_id") String workItemId,
            @JsonProperty("summary") String summary,
            @JsonProperty("description") String description,
            @JsonProperty("work_item_type") WorkItemType workItemType,
            @JsonProperty("org_context") OrgContext orgContext) {
    }

    // ---------------- responses ----------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Department(
            @JsonProperty("department_code") String departmentCode,
            @JsonProperty("confidence") BigDecimal confidence,
            @JsonProperty("reason") String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClarificationQuestion(
            @JsonProperty("id") String id,
            @JsonProperty("question") String question,
            @JsonProperty("field") String field,
            @JsonProperty("required") boolean required) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SensitiveFlags(
            @JsonProperty("security_impact") boolean securityImpact,
            @JsonProperty("production_impact") boolean productionImpact,
            @JsonProperty("budget_impact") boolean budgetImpact,
            @JsonProperty("compliance_impact") boolean complianceImpact) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SuggestedWorkItem(
            @JsonProperty("temp_id") String tempId,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("work_item_type") WorkItemType workItemType,
            @JsonProperty("department_code") String departmentCode,
            @JsonProperty("team_code") String teamCode,
            @JsonProperty("required_skills") List<String> requiredSkills,
            @JsonProperty("affected_components") List<String> affectedComponents,
            @JsonProperty("estimated_hours") BigDecimal estimatedHours,
            @JsonProperty("suggested_story_points") Integer suggestedStoryPoints,
            @JsonProperty("priority") Priority priority,
            @JsonProperty("risk_level") RiskLevel riskLevel,
            @JsonProperty("dependencies") List<String> dependencies,
            @JsonProperty("testing_requirements") List<String> testingRequirements,
            @JsonProperty("closure_type") String closureType,
            @JsonProperty("sensitive_flags") SensitiveFlags sensitiveFlags,
            @JsonProperty("confidence") BigDecimal confidence,
            @JsonProperty("explanation") String explanation,
            @JsonProperty("unresolved_skills") List<String> unresolvedSkills) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnalysisResponse(
            @JsonProperty("work_family") WorkFamily workFamily,
            @JsonProperty("work_item_type") WorkItemType workItemType,
            @JsonProperty("category") String category,
            @JsonProperty("summary") String summary,
            @JsonProperty("project_code") String projectCode,
            @JsonProperty("recommended_team") String recommendedTeam,
            @JsonProperty("departments") List<Department> departments,
            @JsonProperty("affected_components") List<String> affectedComponents,
            @JsonProperty("required_skills") List<String> requiredSkills,
            @JsonProperty("priority") Priority priority,
            @JsonProperty("priority_score") Integer priorityScore,
            @JsonProperty("risk_level") RiskLevel riskLevel,
            @JsonProperty("risk_signals") List<String> riskSignals,
            @JsonProperty("complexity") Complexity complexity,
            @JsonProperty("suggested_story_points") Integer suggestedStoryPoints,
            @JsonProperty("requires_more_information") boolean requiresMoreInformation,
            @JsonProperty("clarification_questions") List<ClarificationQuestion> clarificationQuestions,
            @JsonProperty("requires_decomposition") boolean requiresDecomposition,
            @JsonProperty("matched_process_template") String matchedProcessTemplate,
            @JsonProperty("estimated_total_hours") BigDecimal estimatedTotalHours,
            @JsonProperty("confidence") BigDecimal confidence,
            @JsonProperty("suggested_work_items") List<SuggestedWorkItem> suggestedWorkItems,
            @JsonProperty("security_impact") boolean securityImpact,
            @JsonProperty("production_impact") boolean productionImpact,
            @JsonProperty("budget_impact") boolean budgetImpact,
            @JsonProperty("compliance_impact") boolean complianceImpact,
            @JsonProperty("sla_risk") String slaRisk,
            @JsonProperty("business_impact") String businessImpact,
            @JsonProperty("likely_duplicate_of") String likelyDuplicateOf,
            @JsonProperty("likely_defect") boolean likelyDefect,
            @JsonProperty("suggested_conversion") WorkItemType suggestedConversion,
            @JsonProperty("injection_suspected") boolean injectionSuspected,
            @JsonProperty("explanation") String explanation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProviderMeta(
            @JsonProperty("provider") String provider,
            @JsonProperty("model") String model,
            @JsonProperty("latency_ms") Integer latencyMs,
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens,
            @JsonProperty("is_mock") boolean isMock,
            @JsonProperty("attempts") int attempts,
            @JsonProperty("prompt_version") String promptVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnalysisEnvelope(
            @JsonProperty("analysis") AnalysisResponse analysis,
            @JsonProperty("meta") ProviderMeta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DecomposeResponse(
            @JsonProperty("work_item_id") String workItemId,
            @JsonProperty("suggested_work_items") List<SuggestedWorkItem> suggestedWorkItems,
            @JsonProperty("warnings") List<String> warnings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DecomposeEnvelope(
            @JsonProperty("decomposition") DecomposeResponse decomposition,
            @JsonProperty("meta") ProviderMeta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedSkill(
            @JsonProperty("skill_code") String skillCode,
            @JsonProperty("confidence") BigDecimal confidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractSkillsResponse(
            @JsonProperty("skills") List<ExtractedSkill> skills,
            @JsonProperty("unresolved_skills") List<String> unresolvedSkills,
            @JsonProperty("estimated_hours") BigDecimal estimatedHours,
            @JsonProperty("confidence") BigDecimal confidence,
            @JsonProperty("explanation") String explanation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractSkillsEnvelope(
            @JsonProperty("extraction") ExtractSkillsResponse extraction,
            @JsonProperty("meta") ProviderMeta meta) {
    }

    /** RFC-shaped 422 body from the AI service's repair-exhausted path (docs/07 §8). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnalysisValidationError(
            @JsonProperty("errors") List<String> errors,
            @JsonProperty("raw_output_hash") String rawOutputHash,
            @JsonProperty("attempts") int attempts,
            @JsonProperty("injection_suspected") boolean injectionSuspected) {
    }
}
