package com.smartwork.process.domain;

import com.smartwork.common.domain.TenantEntity;
import com.smartwork.workitem.domain.ClosureType;
import com.smartwork.workitem.domain.Priority;
import com.smartwork.workitem.domain.WorkFamily;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable plan: the set of items a request explodes into.
 *
 * <p>Not to be confused with a workflow definition. A workflow describes the states one
 * item moves through; a template describes how many items there are and who owns each.
 * Onboarding is one template producing nine tasks across three departments, each of which
 * then runs its own SERVICE workflow.
 *
 * <p>Templates exist so the AI has something to <em>customize</em> rather than invent. A
 * model asked to plan onboarding from scratch produces a different plan every time; asked
 * to adapt a known nine-step plan to this specific hire, it produces the same nine steps
 * with the details filled in. That is the difference between a demo and a product.
 */
@Entity
@Table(name = "process_templates")
public class ProcessTemplate extends TenantEntity {

    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "category", length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_family", nullable = false, length = 32)
    private WorkFamily workFamily = WorkFamily.PROCESS_WORK;

    @Column(name = "owner_department_id")
    private UUID ownerDepartmentId;

    @Column(name = "default_process_owner_id")
    private UUID defaultProcessOwnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", nullable = false, length = 16)
    private Priority defaultPriority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure_type", nullable = false, length = 32)
    private ClosureType closureType = ClosureType.PROCESS_OWNER_VERIFICATION;

    @Column(name = "match_keywords", columnDefinition = "text")
    private String matchKeywords;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Tasks are a genuine composition - a template task has no meaning outside its
     * template, and the pair is always loaded and edited together. This is the one place
     * a cascading collection is the right call rather than a convenience.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "process_template_id", nullable = false)
    @OrderBy("sequence ASC")
    private List<ProcessTemplateTask> tasks = new ArrayList<>();

    protected ProcessTemplate() {
    }

    public ProcessTemplate(UUID organizationId, String code, String name) {
        super(organizationId);
        this.code = code;
        this.name = name;
    }

    public void addTask(ProcessTemplateTask task) {
        tasks.add(task);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public WorkFamily getWorkFamily() {
        return workFamily;
    }

    public void setWorkFamily(WorkFamily workFamily) {
        this.workFamily = workFamily;
    }

    public UUID getOwnerDepartmentId() {
        return ownerDepartmentId;
    }

    public void setOwnerDepartmentId(UUID ownerDepartmentId) {
        this.ownerDepartmentId = ownerDepartmentId;
    }

    public UUID getDefaultProcessOwnerId() {
        return defaultProcessOwnerId;
    }

    public void setDefaultProcessOwnerId(UUID defaultProcessOwnerId) {
        this.defaultProcessOwnerId = defaultProcessOwnerId;
    }

    public Priority getDefaultPriority() {
        return defaultPriority;
    }

    public void setDefaultPriority(Priority defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public ClosureType getClosureType() {
        return closureType;
    }

    public void setClosureType(ClosureType closureType) {
        this.closureType = closureType;
    }

    public String getMatchKeywords() {
        return matchKeywords;
    }

    public void setMatchKeywords(String matchKeywords) {
        this.matchKeywords = matchKeywords;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }

    public List<ProcessTemplateTask> getTasks() {
        return List.copyOf(tasks);
    }

    /** Distinct departments this template's tasks span — a PROCESS_OWNER_REVIEW trigger. */
    public long departmentSpan() {
        return tasks.stream()
                .map(ProcessTemplateTask::getDepartmentId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
    }

    public java.math.BigDecimal totalEstimatedHours() {
        return tasks.stream()
                .map(ProcessTemplateTask::getEstimatedHours)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}
