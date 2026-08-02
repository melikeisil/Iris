package com.smartwork.policy.application;

import com.smartwork.analysis.application.AnalysisService;
import com.smartwork.analysis.application.RecommendationAccess;
import com.smartwork.assignment.application.CandidateFeatureAssembler;
import com.smartwork.assignment.application.PolicyResolver;
import com.smartwork.assignment.application.RankingService;
import com.smartwork.assignment.domain.CandidateFeatures;
import com.smartwork.assignment.domain.CandidateFeatures.RequiredSkill;
import com.smartwork.assignment.domain.CandidateFeatures.WorkItemContext;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.identity.application.SkillService;
import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.policy.domain.RoutingInputs;
import com.smartwork.team.application.TeamDirectory;
import com.smartwork.workitem.application.WorkItemAssignment;
import com.smartwork.workitem.domain.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The one public entry point for the whole Phase 5 + Phase 6 pipeline.
 *
 * <p>Everything below it was already implemented and unit-tested, but only reachable by a
 * caller able to hand-assemble a {@code Ranking}, a {@code RoutingInputs} and an
 * {@code ApproverResolver.Target}. That made the pipeline verifiable in pieces and
 * unverifiable as a whole, which is the wrong shape: the interesting behaviour — a
 * low-risk task assigning itself while the one that touches budget stops for a human — is
 * a property of the sequence, not of any step in it.
 *
 * <p>Given a work item id this does all six steps: read the analysis, discover and rank
 * candidates, open an ASSIGNMENT recommendation, evaluate the policy, and either assign or
 * open an approval request in the right queue.
 */
@Service
public class WorkItemRoutingService {

    private static final Logger log = LoggerFactory.getLogger(WorkItemRoutingService.class);

    /**
     * Minimum proficiency a skill from the analysis is scored against.
     *
     * <p>Note that skills coming from the AI are treated as <em>optional</em> — they feed
     * {@code skillScore}, which is 30% of the total, but they never hard-exclude anyone.
     * Marking four AI-suggested skills mandatory would empty the pool on most items and
     * route everything to a team lead with NO_ELIGIBLE_CANDIDATE, which looks like the
     * policy working and is actually the input being wrong. Hard exclusion is reserved for
     * skills a human marks mandatory on the item.
     */
    private static final int DEFAULT_MIN_PROFICIENCY = 2;

    private final WorkItemAssignment workItems;
    private final RecommendationAccess recommendations;
    private final RankingService rankingService;
    private final RoutingService routingService;
    private final RoutingInputsAssembler inputsAssembler;
    private final PolicyResolver policyResolver;
    private final SkillService skills;
    private final DepartmentDirectory departments;
    private final TeamDirectory teams;
    private final AnalysisService analysisService;

    public WorkItemRoutingService(
            WorkItemAssignment workItems,
            RecommendationAccess recommendations,
            RankingService rankingService,
            RoutingService routingService,
            RoutingInputsAssembler inputsAssembler,
            PolicyResolver policyResolver,
            SkillService skills,
            DepartmentDirectory departments,
            TeamDirectory teams,
            AnalysisService analysisService) {
        this.workItems = workItems;
        this.recommendations = recommendations;
        this.rankingService = rankingService;
        this.routingService = routingService;
        this.inputsAssembler = inputsAssembler;
        this.policyResolver = policyResolver;
        this.skills = skills;
        this.departments = departments;
        this.teams = teams;
        this.analysisService = analysisService;
    }

    @Transactional
    public RoutingService.Outcome route(AuthPrincipal principal, UUID workItemId) {
        UUID organizationId = principal.organizationId();
        WorkItemAssignment.RoutingView item = workItems.routingView(organizationId, workItemId);

        RecommendationAccess.View analysis =
                recommendations.findLatestAnalysis(organizationId, workItemId).orElse(null);

        if (analysis == null) {
            // Routing without an analysis is possible but produces a decision built on
            // nothing, so refuse rather than quietly emitting MANUAL_EXCEPTION_REVIEW and
            // letting the caller think the policy engine had an opinion.
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "This work item has not been analyzed. POST /work-items/" + workItemId
                            + "/analyze first, then route.");
        }

        // --- 1-3: discover, filter, score ------------------------------------
        List<String> resolvedSkills = resolveSkillCodes(organizationId, analysis);
        List<String> components = analysis.stringList("affected_components");

        WorkItemContext context = new WorkItemContext(
                resolvedSkills.stream()
                        .map(code -> new RequiredSkill(code, DEFAULT_MIN_PROFICIENCY, false))
                        .toList(),
                components,
                item.estimatedHours(),
                item.dueDate() != null,
                requiresIndependentReview(item),
                item.productionImpacting(),
                item.budgetSensitive(),
                item.securitySensitive(),
                item.priority() == null ? Priority.MEDIUM : item.priority(),
                analysis.text("category"));

        UUID recommendedTeamId = resolveTeam(organizationId, item, analysis);
        UUID recommendedDepartmentId = resolveDepartment(organizationId, item, analysis);

        CandidateFeatureAssembler.ItemFacts facts = new CandidateFeatureAssembler.ItemFacts(
                item.reporterId(), item.projectId(), recommendedTeamId, recommendedDepartmentId,
                resolvedSkills, List.of(), components, item.dueDate());

        RankingService.Ranking ranking =
                rankingService.rank(organizationId, context, facts, hasSeniorReviewer(item));

        // --- 4: open the recommendation the decision is recorded against -----
        UUID recommendationId = recommendations.openAssignmentRecommendation(
                organizationId, workItemId, item.updatedAt());

        // --- 5-6: evaluate the policy, then assign or open an approval -------
        RoutingInputs inputs = inputsAssembler.assemble(
                item, analysis, analysis.analysisPayload(), ranking, planFactsFor(analysis));

        Optional<PolicyResolver.PolicyView> policy =
                policyResolver.resolve(organizationId, recommendedDepartmentId, recommendedTeamId);

        ApproverResolver.Target target = new ApproverResolver.Target(
                organizationId, recommendedTeamId, recommendedDepartmentId,
                item.processOwnerId(), null, null, item.reporterId(), item.reporterId());

        RoutingService.Outcome outcome = routingService.route(
                principal, workItemId, recommendationId, inputs, ranking, target, policy);

        log.info("Routed {} -> {} ({} candidates ranked, {} excluded)",
                item.referenceKey(), outcome.decision().route(),
                ranking.ranked().size(), ranking.excluded().size());

        return outcome;
    }

    /**
     * Answers the item's pending clarification questions, reanalyzes with those answers,
     * and - provided the fresh analysis is usable and did not ask for more information
     * again - routes it.
     *
     * <p>Lives here rather than in {@code analysis} because completing "answer, reanalyze,
     * reroute" needs both {@code analysis::application} (to submit the answers and rerun
     * analysis) and this module's own routing pipeline, and {@code analysis}'s module
     * config does not permit it to depend on {@code policy} - only this direction is
     * legal. A requester whose second answer still leaves the AI asking a question is left
     * in {@code WAITING_INFORMATION} rather than being routed on an incomplete analysis;
     * the frontend shows the (possibly new) questions from {@code
     * WorkItemDetailResponse.pendingClarificationQuestions} exactly as it did the first
     * time.
     */
    @Transactional
    public AdditionalInformationOutcome resumeAfterAdditionalInformation(
            AuthPrincipal principal, UUID workItemId,
            List<AnalysisService.ClarificationAnswerInput> answers) {

        String statusAfterReanalysis =
                analysisService.answerClarificationAndReanalyze(principal, workItemId, answers);

        RecommendationAccess.View freshAnalysis = recommendations
                .findLatestAnalysis(principal.organizationId(), workItemId)
                .orElse(null);

        // Two ways "still needs information" can show up: the item is back in
        // WAITING_INFORMATION (workflow profiles that define NEEDS_INFO), or the fresh
        // analysis's own flag says so (profiles that do not - the flag is the more
        // fundamental signal, so it is checked either way).
        boolean stillNeedsInformation = "WAITING_INFORMATION".equals(statusAfterReanalysis)
                || (freshAnalysis != null && freshAnalysis.flag("requires_more_information"));
        boolean readyToRoute = !stillNeedsInformation
                && freshAnalysis != null
                && !"FAILED".equals(freshAnalysis.status());

        RoutingService.Outcome routingOutcome = null;
        if (readyToRoute) {
            try {
                routingOutcome = route(principal, workItemId);
            } catch (RuntimeException e) {
                // The answers are saved and the item is back in AI_ANALYSIS regardless of
                // whether routing succeeds - a routing hiccup here should not look like the
                // clarification submission itself failed, and must not roll it back.
                log.warn("Automatic routing after clarification answers failed for {}: {}",
                        workItemId, e.getMessage());
            }
        }

        // A narrow projection, not the entity: this module may not depend on workitem's
        // JPA types (ArchitectureTest.modulesMustNotDependOnAnotherModulesEntities), the
        // same reason routing itself reads through WorkItemAssignment.RoutingView rather
        // than the WorkItem class.
        WorkItemAssignment.RoutingView item = workItems.routingView(principal.organizationId(), workItemId);
        return new AdditionalInformationOutcome(
                item.workItemId(), item.referenceKey(), item.status(), freshAnalysis, routingOutcome);
    }

    public record AdditionalInformationOutcome(
            UUID workItemId, String referenceKey, String status,
            RecommendationAccess.View analysis, RoutingService.Outcome routing) {
    }

    /** Maps the AI's skill strings onto canonical codes, dropping anything unrecognised. */
    private List<String> resolveSkillCodes(UUID organizationId, RecommendationAccess.View analysis) {
        List<String> raw = analysis.stringList("required_skills");
        if (raw.isEmpty()) {
            return List.of();
        }
        SkillService.SkillResolution resolution = skills.resolve(organizationId, raw);
        if (!resolution.unresolved().isEmpty()) {
            // Surfaced rather than silently dropped: an unresolved skill means the AI named
            // something outside the vocabulary, which is a curation signal for an Admin.
            log.info("Analysis named {} unknown skill(s): {}",
                    resolution.unresolved().size(), resolution.unresolved());
        }
        return resolution.resolved().stream().map(SkillService.SkillView::code).toList();
    }

    private UUID resolveTeam(
            UUID organizationId, WorkItemAssignment.RoutingView item, RecommendationAccess.View analysis) {
        if (item.teamId() != null) {
            return item.teamId();
        }
        String code = analysis.text("recommended_team");
        if (code == null) {
            return null;
        }
        return teams.listAll(organizationId, null).stream()
                .filter(team -> team.code().equalsIgnoreCase(code))
                .map(TeamDirectory.TeamView::id)
                .findFirst()
                .orElse(null);
    }

    /**
     * The item's own department wins; otherwise the highest-confidence department the
     * analysis named. Departments arrive sorted by the model but that is not guaranteed,
     * so the first entry is used only as a tiebreak after an explicit assignment.
     */
    @SuppressWarnings("unchecked")
    private UUID resolveDepartment(
            UUID organizationId, WorkItemAssignment.RoutingView item, RecommendationAccess.View analysis) {
        if (item.departmentId() != null) {
            return item.departmentId();
        }
        Object raw = analysis.analysisPayload() == null
                ? null : analysis.analysisPayload().get("departments");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        String best = null;
        double bestConfidence = -1;
        for (Object entry : list) {
            if (entry instanceof java.util.Map<?, ?> map) {
                Object code = map.get("department_code");
                Object confidence = map.get("confidence");
                double c = confidence instanceof Number n ? n.doubleValue() : 0;
                if (code instanceof String s && c > bestConfidence) {
                    best = s;
                    bestConfidence = c;
                }
            }
        }
        return best == null ? null
                : departments.findByCode(organizationId, best)
                        .map(DepartmentDirectory.DepartmentView::id)
                        .orElse(null);
    }

    /** Derives the plan-shaped signals the policy engine needs from the stored analysis. */
    private RoutingInputsAssembler.PlanFacts planFactsFor(RecommendationAccess.View analysis) {
        List<String> components = analysis.stringList("affected_components");
        Object hours = analysis.analysisPayload() == null
                ? null : analysis.analysisPayload().get("estimated_total_hours");

        int departmentCount = Math.max(1, departmentCountOf(analysis));

        return new RoutingInputsAssembler.PlanFacts(
                departmentCount,
                1,
                components.size(),
                hours instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : null,
                analysis.text("matched_process_template") != null,
                false,
                !analysis.flag("requires_more_information"),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                analysis.confidence());
    }

    private int departmentCountOf(RecommendationAccess.View analysis) {
        Object raw = analysis.analysisPayload() == null
                ? null : analysis.analysisPayload().get("departments");
        return raw instanceof List<?> list ? list.size() : 1;
    }

    /** Service work verified by its requester needs someone other than the requester on it. */
    private boolean requiresIndependentReview(WorkItemAssignment.RoutingView item) {
        return "SERVICE_WORK".equals(item.workFamily()) || "INCIDENT_WORK".equals(item.workFamily());
    }

    /**
     * Whether a senior reviewer is already named, which is what the INTERN mentor
     * restriction checks. Absent a reviewer, interns are excluded rather than assumed
     * supervised — the restriction exists precisely for the unsupervised case.
     */
    private boolean hasSeniorReviewer(WorkItemAssignment.RoutingView item) {
        return item.reviewerId() != null;
    }
}
