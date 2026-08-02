package com.smartwork.identity.api;

import com.smartwork.common.tenancy.OrganizationContext;
import com.smartwork.identity.application.SkillService;
import com.smartwork.identity.domain.SkillCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/skills")
@Tag(name = "admin")
public class AdminSkillController {

    private final SkillService skills;

    public AdminSkillController(SkillService skills) {
        this.skills = skills;
    }

    @GetMapping
    @Operation(summary = "List the canonical skill vocabulary")
    public List<SkillService.SkillView> list() {
        return skills.listAll(OrganizationContext.require());
    }

    @PostMapping
    @Operation(summary = "Add a skill",
            description = "Codes are normalized to UPPER_SNAKE_CASE. A non-conforming code is rejected "
                    + "rather than silently coerced, so the vocabulary cannot drift.")
    public ResponseEntity<SkillService.SkillView> create(@Valid @RequestBody CreateSkillRequest request) {
        return ResponseEntity.status(201).body(skills.create(
                OrganizationContext.require(), request.code(), request.name(), request.category()));
    }

    @PostMapping("/{id}/aliases")
    @Operation(summary = "Add an alias that maps raw AI output onto this skill")
    public SkillService.SkillView addAlias(@PathVariable UUID id,
                                           @Valid @RequestBody AddAliasRequest request) {
        return skills.addAlias(OrganizationContext.require(), id, request.alias());
    }

    @PostMapping("/resolve")
    @Operation(summary = "Resolve raw skill strings onto canonical skills",
            description = "Unmatched codes are returned in `unresolved` and are never auto-created; "
                    + "they are surfaced for an Admin to curate.")
    public SkillService.SkillResolution resolve(@Valid @RequestBody ResolveSkillsRequest request) {
        return skills.resolve(OrganizationContext.require(), request.codes());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a skill")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        skills.deactivate(OrganizationContext.require(), id);
        return ResponseEntity.noContent().build();
    }

    public record CreateSkillRequest(@NotBlank @Size(max = 64) String code,
                                     @NotBlank @Size(max = 255) String name,
                                     @NotNull SkillCategory category) {
    }

    public record AddAliasRequest(@NotBlank @Size(max = 128) String alias) {
    }

    public record ResolveSkillsRequest(@NotNull List<String> codes) {
    }
}
