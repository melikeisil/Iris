package com.smartwork.process.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.process.domain.ProcessTemplate;
import com.smartwork.process.domain.ProcessTemplateTask;
import com.smartwork.process.domain.TemplateMatcher;
import com.smartwork.process.infrastructure.ProcessTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read and match side of the template catalogue. */
@Service
public class ProcessTemplateService {

    private final ProcessTemplateRepository templates;

    public ProcessTemplateService(ProcessTemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional(readOnly = true)
    public List<TemplateSummaryView> listAll(UUID organizationId) {
        return templates.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(TemplateSummaryView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateDetailsView require(UUID organizationId, UUID templateId) {
        return templates.findByOrganizationIdAndId(organizationId, templateId)
                .map(TemplateDetailsView::from)
                .orElseThrow(() -> ApiException.notFound("Process template", templateId));
    }

    @Transactional(readOnly = true)
    public Optional<TemplateDetailsView> findByCode(UUID organizationId, String code) {
        return templates.findByOrganizationIdAndCode(organizationId, code)
                .map(TemplateDetailsView::from);
    }

    @Transactional(readOnly = true)
    public ProcessTemplate requireForInstantiation(UUID organizationId, UUID templateId) {
        return templates.findByOrganizationIdAndId(organizationId, templateId)
                .orElseThrow(() -> ApiException.notFound("Process template", templateId));
    }

    /** Deterministic keyword match against the whole active catalogue. */
    @Transactional(readOnly = true)
    public Optional<TemplateMatcher.Match> match(UUID organizationId, String summary, String description) {
        return TemplateMatcher.bestMatch(summary, description, templates.findActiveWithTasks(organizationId));
    }

    /**
     * Reconciles the deterministic match with the AI's suggestion.
     *
     * <p>Agreement raises confidence in both. Disagreement is not resolved by preferring one
     * source — it is reported, because two independent classifiers disagreeing about what
     * kind of request this is, is exactly the situation a Process Owner should look at
     * rather than have decided for them.
     */
    @Transactional(readOnly = true)
    public MatchOutcome reconcile(
            UUID organizationId, String summary, String description, String aiSuggestedCode) {

        Optional<TemplateMatcher.Match> deterministic = match(organizationId, summary, description);
            Optional<ProcessTemplate> aiSuggested = aiSuggestedCode == null
                ? Optional.empty()
                : templates.findByOrganizationIdAndCode(organizationId, aiSuggestedCode);

        if (deterministic.isEmpty() && aiSuggested.isEmpty()) {
            return new MatchOutcome(null, 0.0, Agreement.NEITHER_MATCHED);
        }
        if (deterministic.isPresent() && aiSuggested.isEmpty()) {
            return new MatchOutcome(TemplateDetailsView.from(deterministic.get().template()),
                    deterministic.get().confidence(), Agreement.KEYWORD_ONLY);
        }
        if (deterministic.isEmpty()) {
            return new MatchOutcome(TemplateDetailsView.from(aiSuggested.get()), 0.5, Agreement.AI_ONLY);
        }
        boolean agree = deterministic.get().template().getId().equals(aiSuggested.get().getId());
        return agree
                ? new MatchOutcome(TemplateDetailsView.from(deterministic.get().template()),
                        Math.min(1.0, deterministic.get().confidence() + 0.25), Agreement.BOTH_AGREE)
                : new MatchOutcome(TemplateDetailsView.from(deterministic.get().template()),
                        deterministic.get().confidence(), Agreement.DISAGREE);
    }

    public enum Agreement {
        BOTH_AGREE,
        KEYWORD_ONLY,
        AI_ONLY,
        /** Both matched, but to different templates — a review trigger, not a tiebreak. */
        DISAGREE,
        NEITHER_MATCHED
    }

    public record MatchOutcome(TemplateDetailsView template, double confidence, Agreement agreement) {

        public boolean matched() {
            return template != null;
        }

        /** docs/08 §5.3 makes an unmatched PROCESS_WORK item a PROCESS_OWNER_REVIEW trigger. */
        public boolean needsHumanReview() {
            return agreement == Agreement.DISAGREE || agreement == Agreement.NEITHER_MATCHED;
        }
    }

    public record TemplateTaskView(
            long sequence,
            String title,
            String description,
            java.math.BigDecimal estimatedHours) {

        static TemplateTaskView from(ProcessTemplateTask task) {
            return new TemplateTaskView(
                    task.getSequence(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getEstimatedHours());
        }
    }

    public record TemplateSummaryView(
            UUID id,
            String code,
            String name,
            String category,
            boolean active) {

        static TemplateSummaryView from(ProcessTemplate template) {
            return new TemplateSummaryView(
                    template.getId(),
                    template.getCode(),
                    template.getName(),
                    template.getCategory(),
                    template.isActive());
        }
    }

    public record TemplateDetailsView(
            UUID id,
            String code,
            String name,
            String description,
            String category,
            UUID defaultProcessOwnerId,
            boolean active,
            List<TemplateTaskView> tasks) {

        static TemplateDetailsView from(ProcessTemplate template) {
            return new TemplateDetailsView(
                    template.getId(),
                    template.getCode(),
                    template.getName(),
                    template.getDescription(),
                    template.getCategory(),
                    template.getDefaultProcessOwnerId(),
                    template.isActive(),
                    template.getTasks().stream().map(TemplateTaskView::from).toList());
        }
    }
}
