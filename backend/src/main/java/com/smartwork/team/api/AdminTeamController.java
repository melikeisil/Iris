package com.smartwork.team.api;

import com.smartwork.common.tenancy.OrganizationContext;
import com.smartwork.team.application.TeamDirectory;
import com.smartwork.team.application.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/teams")
@Tag(name = "admin")
public class AdminTeamController {

    private final TeamService teams;

    public AdminTeamController(TeamService teams) {
        this.teams = teams;
    }

    @GetMapping
    @Operation(summary = "List teams, optionally filtered by department")
    public List<TeamDirectory.TeamView> list(@RequestParam(required = false) UUID departmentId) {
        return teams.listAll(OrganizationContext.require(), departmentId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one team")
    public TeamDirectory.TeamView get(@PathVariable UUID id) {
        return teams.find(OrganizationContext.require(), id)
                .orElseThrow(() -> com.smartwork.common.error.ApiException.notFound("Team", id));
    }

    @PostMapping
    @Operation(summary = "Create a team")
    public ResponseEntity<TeamDirectory.TeamView> create(@Valid @RequestBody CreateTeamRequest request) {
        return ResponseEntity.status(201).body(teams.create(OrganizationContext.require(),
                request.departmentId(), request.code(), request.name(), request.weeklyCapacityHours()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a team, its lead and its capacity")
    public TeamDirectory.TeamView update(@PathVariable UUID id, @Valid @RequestBody UpdateTeamRequest request) {
        return teams.update(OrganizationContext.require(), id, request.name(),
                request.leadUserId(), request.weeklyCapacityHours(), request.active());
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "List team members with their allocation")
    public List<TeamDirectory.MemberView> members(@PathVariable UUID id) {
        return teams.membersOf(OrganizationContext.require(), id);
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add a member",
            description = "Allocation is the share of this person's capacity the team may claim. "
                    + "Exactly one membership per person is primary.")
    public ResponseEntity<TeamDirectory.MemberView> addMember(@PathVariable UUID id,
                                                              @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(201).body(teams.addMember(OrganizationContext.require(), id,
                request.userId(), request.allocationPercent(), request.primaryTeam()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Remove a member")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        teams.removeMember(OrganizationContext.require(), id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a team (soft)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        teams.delete(OrganizationContext.require(), id);
        return ResponseEntity.noContent().build();
    }

    public record CreateTeamRequest(@NotNull UUID departmentId,
                                    @NotBlank @Size(max = 64) String code,
                                    @NotBlank @Size(max = 255) String name,
                                    BigDecimal weeklyCapacityHours) {
    }

    public record UpdateTeamRequest(@NotBlank @Size(max = 255) String name,
                                    UUID leadUserId,
                                    BigDecimal weeklyCapacityHours,
                                    boolean active) {
    }

    public record AddMemberRequest(@NotNull UUID userId,
                                   @Min(1) @Max(100) int allocationPercent,
                                   boolean primaryTeam) {
    }
}
