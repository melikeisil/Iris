package com.smartwork.identity.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.identity.domain.Skill;
import com.smartwork.identity.domain.SkillAlias;
import com.smartwork.identity.domain.SkillCategory;
import com.smartwork.identity.domain.User;
import com.smartwork.identity.domain.UserSkill;
import com.smartwork.identity.infrastructure.SkillAliasRepository;
import com.smartwork.identity.infrastructure.SkillRepository;
import com.smartwork.identity.infrastructure.UserRepository;
import com.smartwork.identity.infrastructure.UserSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the canonical skill vocabulary and the alias resolution that keeps AI output from
 * degrading it.
 */
@Service
@Transactional(readOnly = true)
public class SkillService {

    private final SkillRepository skills;
    private final SkillAliasRepository aliases;
    private final UserSkillRepository userSkills;
    private final UserRepository users;
    private final Clock clock;

    public SkillService(SkillRepository skills, SkillAliasRepository aliases,
                        UserSkillRepository userSkills, UserRepository users, Clock clock) {
        this.skills = skills;
        this.aliases = aliases;
        this.userSkills = userSkills;
        this.users = users;
        this.clock = clock;
    }

    public List<SkillView> listAll(UUID organizationId) {
        return skills.findAllInOrganization(organizationId).stream().map(SkillService::toView).toList();
    }

    @Transactional
    public SkillView create(UUID organizationId, String code, String name, SkillCategory category) {
        String canonical;
        try {
            canonical = Skill.requireCanonical(code);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(com.smartwork.common.error.ErrorCode.VALIDATION_FAILED, invalid.getMessage());
        }
        if (skills.findByCode(organizationId, canonical).isPresent()) {
            throw ApiException.duplicate("Skill '" + canonical + "' already exists.");
        }
        return toView(skills.save(new Skill(organizationId, canonical, name, category)));
    }

    @Transactional
    public SkillView addAlias(UUID organizationId, UUID skillId, String alias) {
        Skill skill = requireSkill(organizationId, skillId);
        aliases.save(new SkillAlias(skill, alias));
        return toView(skill);
    }

    @Transactional
    public void deactivate(UUID organizationId, UUID skillId) {
        requireSkill(organizationId, skillId).deactivate(clock.instant());
    }

    /**
     * Resolves raw skill strings - typically straight from an LLM - onto canonical skills.
     *
     * <p>Unmatched codes are returned separately and are never auto-created: a vocabulary
     * that grows itself at runtime stops being a vocabulary. An Admin curates them.
     */
    public SkillResolution resolve(UUID organizationId, List<String> rawCodes) {
        Map<String, Skill> byCode = new HashMap<>();
        skills.findAllInOrganization(organizationId).forEach(skill -> byCode.put(skill.getCode(), skill));

        Map<String, Skill> byAlias = new HashMap<>();
        aliases.findAllInOrganization(organizationId)
                .forEach(alias -> byAlias.put(alias.getAlias(), alias.getSkill()));

        List<SkillView> resolved = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        for (String raw : rawCodes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String upper = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
            Skill match = byCode.get(upper);
            if (match == null) {
                match = byAlias.get(SkillAlias.normalize(raw));
            }
            if (match == null) {
                unresolved.add(raw.trim());
            } else {
                resolved.add(toView(match));
            }
        }
        return new SkillResolution(List.copyOf(resolved), List.copyOf(unresolved));
    }

    public List<UserSkillView> skillsOf(UUID organizationId, UUID userId) {
        requireUser(organizationId, userId);
        return userSkills.findByUser(userId).stream()
                .map(userSkill -> new UserSkillView(
                        userSkill.getSkill().getId(), userSkill.getSkill().getCode(),
                        userSkill.getSkill().getName(), userSkill.getProficiency(),
                        userSkill.getYearsExperience(), userSkill.getLastUsedAt()))
                .toList();
    }

    @Transactional
    public UserSkillView setUserSkill(UUID organizationId, UUID userId, UUID skillId,
                                      int proficiency, BigDecimal yearsExperience, LocalDate lastUsedAt) {
        User user = requireUser(organizationId, userId);
        Skill skill = requireSkill(organizationId, skillId);

        UserSkill userSkill = userSkills.findByUserIdAndSkillId(userId, skillId).orElse(null);
        try {
            if (userSkill == null) {
                userSkill = userSkills.save(
                        new UserSkill(user, skill, proficiency, yearsExperience, lastUsedAt));
            } else {
                userSkill.update(proficiency, yearsExperience, lastUsedAt);
            }
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(com.smartwork.common.error.ErrorCode.VALIDATION_FAILED, invalid.getMessage());
        }

        return new UserSkillView(skill.getId(), skill.getCode(), skill.getName(),
                userSkill.getProficiency(), userSkill.getYearsExperience(), userSkill.getLastUsedAt());
    }

    @Transactional
    public void removeUserSkill(UUID organizationId, UUID userId, UUID skillId) {
        requireUser(organizationId, userId);
        userSkills.deleteByUserIdAndSkillId(userId, skillId);
    }

    private Skill requireSkill(UUID organizationId, UUID skillId) {
        return skills.findByIdInOrganization(skillId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Skill", skillId));
    }

    private User requireUser(UUID organizationId, UUID userId) {
        return users.findByIdInOrganization(userId, organizationId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
    }

    private static SkillView toView(Skill skill) {
        return new SkillView(skill.getId(), skill.getCode(), skill.getName(),
                skill.getCategory().name(), skill.isActive());
    }

    public record SkillView(UUID id, String code, String name, String category, boolean active) {
    }

    public record UserSkillView(UUID skillId, String code, String name, short proficiency,
                                BigDecimal yearsExperience, LocalDate lastUsedAt) {
    }

    public record SkillResolution(List<SkillView> resolved, List<String> unresolved) {
    }
}
