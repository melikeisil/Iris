package com.smartwork.process.domain;

import com.smartwork.common.domain.BaseEntity;
import com.smartwork.workitem.domain.Priority;
import com.smartwork.workitem.domain.WorkItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** One step of a {@link ProcessTemplate}. */
@Entity
@Table(name = "process_template_tasks")
public class ProcessTemplateTask extends BaseEntity {

    /**
     * Template-local identifier. Deliberately not the primary key: authors reorder steps by
     * editing numbers, and {@code depends_on_sequences} referencing UUIDs would make the
     * seed files unreadable and unreviewable.
     */
    @Column(name = "sequence", nullable = false)
    private short sequence;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_item_type", nullable = false, length = 24)
    private WorkItemType workItemType = WorkItemType.TASK;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "estimated_hours", precision = 6, scale = 2)
    private BigDecimal estimatedHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 16)
    private Priority priority;

    @Column(name = "required_skill_codes", length = 512)
    private String requiredSkillCodes;

    @Column(name = "depends_on_sequences", length = 128)
    private String dependsOnSequences;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    protected ProcessTemplateTask() {
    }

    public ProcessTemplateTask(int sequence, String title) {
        this.sequence = (short) sequence;
        this.title = title;
    }

    public List<String> requiredSkills() {
        return csv(requiredSkillCodes);
    }

    /** Sibling sequences this step waits on, resolved into real links at instantiation. */
    public List<Integer> dependsOn() {
        List<Integer> out = new ArrayList<>();
        for (String token : csv(dependsOnSequences)) {
            try {
                out.add(Integer.parseInt(token));
            } catch (NumberFormatException ignored) {
                // A malformed dependency is dropped rather than failing instantiation:
                // one bad token in a template should not make the whole process
                // uninstantiable at the moment someone is trying to onboard a new hire.
            }
        }
        return out;
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public short getSequence() {
        return sequence;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WorkItemType getWorkItemType() {
        return workItemType;
    }

    public void setWorkItemType(WorkItemType workItemType) {
        this.workItemType = workItemType;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public void setEstimatedHours(BigDecimal estimatedHours) {
        this.estimatedHours = estimatedHours;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public void setRequiredSkillCodes(String requiredSkillCodes) {
        this.requiredSkillCodes = requiredSkillCodes;
    }

    public void setDependsOnSequences(String dependsOnSequences) {
        this.dependsOnSequences = dependsOnSequences;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }
}
