package com.smartwork.assignment.application;

import com.smartwork.assignment.domain.AssignmentPolicy;
import com.smartwork.assignment.domain.LevelRestrictions;
import com.smartwork.assignment.domain.ScoringWeights;
import com.smartwork.assignment.infrastructure.AssignmentPolicyRepository;
import com.smartwork.assignment.infrastructure.EmployeeLevelPolicyRepository;
import com.smartwork.identity.domain.EmployeeLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the effective policy for a scope: team, then department, then organization,
 * then built-in Java defaults (docs/08 §8).
 *
 * <p>The built-in defaults tier is what makes the system work on a fresh database with no
 * configuration at all — an organization that has never opened the policy screen still
 * gets sane, documented behaviour rather than a null pointer or a refusal to rank.
 */
@Service
public class PolicyResolver {

    private final AssignmentPolicyRepository policyRepository;
    private final EmployeeLevelPolicyRepository levelPolicyRepository;

    public PolicyResolver(
            AssignmentPolicyRepository policyRepository,
            EmployeeLevelPolicyRepository levelPolicyRepository) {
        this.policyRepository = policyRepository;
        this.levelPolicyRepository = levelPolicyRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PolicyView> resolve(UUID organizationId, UUID departmentId, UUID teamId) {
        List<AssignmentPolicy> chain =
                policyRepository.findResolutionChain(organizationId, departmentId, teamId);
        return chain.stream().findFirst().map(PolicyView::from);
    }

    /** Effective weights: the most specific policy's overrides, or the documented defaults. */
    @Transactional(readOnly = true)
    public ScoringWeights resolveWeights(UUID organizationId, UUID departmentId, UUID teamId) {
        return resolve(organizationId, departmentId, teamId)
                .map(PolicyView::weights)
                .orElseGet(ScoringWeights::defaults);
    }

    /**
     * Level restrictions for every level in one read. Returns a complete map: any level
     * the organization has not configured falls back to
     * {@link LevelRestrictions#defaultsFor}, so callers never have to null-check a level.
     */
    @Transactional(readOnly = true)
    public Map<EmployeeLevel, LevelRestrictions> resolveLevelRestrictions(UUID organizationId) {
        Map<EmployeeLevel, LevelRestrictions> resolved = new EnumMap<>(EmployeeLevel.class);
        for (EmployeeLevel level : EmployeeLevel.values()) {
            resolved.put(level, LevelRestrictions.defaultsFor(level));
        }
        levelPolicyRepository.findByOrganizationId(organizationId)
                .forEach(policy -> resolved.put(policy.getEmployeeLevel(), policy.toRestrictions()));
        return resolved;
    }

    public record PolicyView(
            UUID id,
            Long version,
            java.math.BigDecimal minAutoAssignConfidence,
            java.math.BigDecimal maxAutoAssignWorkload,
            int maxAutoAssignHours,
            com.smartwork.workitem.domain.RiskLevel autoAssignMaxRisk,
            int minAutoAssignScore,
            int minScoreGap,
            boolean allowCrossDepartmentAuto,
            ScoringWeights weights) {

        static PolicyView from(AssignmentPolicy policy) {
            return new PolicyView(
                    policy.getId(),
                    policy.getVersion(),
                    policy.getMinAutoAssignConfidence(),
                    policy.getMaxAutoAssignWorkload(),
                    policy.getMaxAutoAssignHours(),
                    policy.getAutoAssignMaxRisk(),
                    policy.getMinAutoAssignScore(),
                    policy.getMinScoreGap(),
                    policy.isAllowCrossDepartmentAuto(),
                    policy.resolveWeights());
        }
    }
}
