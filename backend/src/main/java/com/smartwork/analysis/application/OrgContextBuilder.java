package com.smartwork.analysis.application;

import com.smartwork.analysis.infrastructure.AiServiceDtos.OrgContext;
import com.smartwork.analysis.infrastructure.AiServiceDtos.TeamRef;
import com.smartwork.identity.application.SkillService;
import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.organization.application.DepartmentDirectory.DepartmentView;
import com.smartwork.process.application.ProcessTemplateService;
import com.smartwork.project.application.ProjectDirectory;
import com.smartwork.project.application.ProjectDirectory.ProjectView;
import com.smartwork.team.application.TeamDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the vocabulary the model is allowed to choose from (docs/07 §4).
 *
 * <p>This is the mechanism that keeps the AI inside the organization's own language. The
 * model is never asked to invent a department name — it is asked to <em>select</em> from a
 * list, and anything outside that list fails post-validation inside the AI service. That
 * makes the completeness of this method load-bearing in both directions: an incomplete
 * context produces spurious validation failures on legitimate answers, and an over-broad
 * one lets the model name things that do not exist.
 */
@Service
public class OrgContextBuilder {

    private final DepartmentDirectory departments;
    private final TeamDirectory teams;
    private final ProjectDirectory projects;
    private final SkillService skills;
    private final ProcessTemplateService templates;

    public OrgContextBuilder(
            DepartmentDirectory departments,
            TeamDirectory teams,
            ProjectDirectory projects,
            SkillService skills,
            ProcessTemplateService templates) {
        this.departments = departments;
        this.teams = teams;
        this.projects = projects;
        this.skills = skills;
        this.templates = templates;
    }

    @Transactional(readOnly = true)
    public OrgContext build(UUID organizationId) {
        List<DepartmentView> departmentViews = departments.listAll(organizationId);
        Map<UUID, String> departmentCodeById = new HashMap<>();
        List<String> departmentCodes = new ArrayList<>();
        for (DepartmentView department : departmentViews) {
            departmentCodeById.put(department.id(), department.code());
            departmentCodes.add(department.code());
        }

        // listAll(orgId, null) is "all departments"; teams are needed with their owning
        // department code so the model cannot put a Backend task under HR.
        List<TeamRef> teamRefs = teams.listAll(organizationId, null).stream()
                .filter(TeamDirectory.TeamView::active)
                .map(team -> new TeamRef(team.code(), departmentCodeById.get(team.departmentId())))
                .filter(ref -> ref.departmentCode() != null)
                .toList();

        List<ProjectView> projectViews = projects.listAll(organizationId);
        List<String> projectCodes = projectViews.stream().map(ProjectView::code).toList();

        // Component codes are only unique within a project, but the AI contract carries a
        // flat list. Deduplicated here; a collision across two projects is harmless because
        // the backend resolves the component against the item's own project when applying.
        Set<String> componentCodes = new LinkedHashSet<>();
        for (ProjectView project : projectViews) {
            projects.componentsOf(organizationId, project.id()).stream()
                    .filter(ProjectDirectory.ComponentView::active)
                    .forEach(component -> componentCodes.add(component.code()));
        }

        List<String> skillCodes = skills.listAll(organizationId).stream()
                .filter(SkillService.SkillView::active)
                .map(SkillService.SkillView::code)
                .toList();

        List<ProcessTemplateService.TemplateSummaryView> allTemplates = templates.listAll(organizationId);
        List<String> templateCodes = allTemplates.stream()
                .filter(ProcessTemplateService.TemplateSummaryView::active)
                .map(ProcessTemplateService.TemplateSummaryView::code)
                .toList();
        List<String> categoryCodes = allTemplates.stream()
                .map(ProcessTemplateService.TemplateSummaryView::category)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return new OrgContext(
                departmentCodes,
                teamRefs,
                projectCodes,
                List.copyOf(componentCodes),
                skillCodes,
                templateCodes,
                categoryCodes);
    }
}
