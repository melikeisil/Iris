package com.smartwork.identity.api;

import com.smartwork.common.api.PageResponse;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.common.tenancy.OrganizationContext;
import com.smartwork.identity.application.SkillService;
import com.smartwork.identity.application.UserDirectory;
import com.smartwork.identity.application.UserService;
import com.smartwork.identity.domain.EmployeeLevel;
import com.smartwork.identity.domain.SystemRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Administrative user management.
 *
 * <p>Admin is deliberately not an operational role: there is no endpoint here to assign
 * work or approve anything. An Admin who needs to approve must also hold MANAGER.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "admin", description = "Configuration. Requires the ADMIN role.")
public class AdminUserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;
    private final SkillService skillService;

    public AdminUserController(UserService userService, SkillService skillService) {
        this.userService = userService;
        this.skillService = skillService;
    }

    @GetMapping
    @Operation(summary = "Search users in the caller's organization")
    public PageResponse<UserResponse> search(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(MAX_PAGE_SIZE) int size) {

        Page<UserDirectory.UserSummary> results = userService.search(
                OrganizationContext.require(), departmentId, activeOnly, q,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by("fullName").ascending()));

        return PageResponse.from(results, UserResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one user")
    public UserResponse get(@PathVariable UUID id) {
        return UserResponse.from(userService.get(OrganizationContext.require(), id));
    }

    @PostMapping
    @Operation(summary = "Create a user")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        var created = userService.create(OrganizationContext.require(),
                new UserService.CreateUserCommand(
                        request.email(), request.password(), request.fullName(),
                        request.employeeLevel(), request.weeklyCapacityHours(),
                        request.departmentId(), request.organizationWide(),
                        request.roles() == null ? Set.of() : request.roles(),
                        principal.userId()));

        return ResponseEntity.status(201).body(UserResponse.from(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user's profile")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(userService.update(OrganizationContext.require(), id,
                new UserService.UpdateUserCommand(request.fullName(), request.employeeLevel(),
                        request.weeklyCapacityHours(), request.departmentId(),
                        request.organizationWide(), request.active())));
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Replace a user's system roles",
            description = "Roles are additive in effect: EMPLOYEE is re-granted if the new set omits it.")
    public UserResponse replaceRoles(@PathVariable UUID id,
                                     @Valid @RequestBody ReplaceRolesRequest request,
                                     @AuthenticationPrincipal AuthPrincipal principal) {
        return UserResponse.from(userService.replaceRoles(
                OrganizationContext.require(), id, request.roles(), principal.userId()));
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Reset a user's password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(OrganizationContext.require(), id, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a user (soft delete)")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        userService.deactivate(OrganizationContext.require(), id);
        return ResponseEntity.noContent().build();
    }

    // ---- Skills ----

    @GetMapping("/{id}/skills")
    @Operation(summary = "List a user's skills with proficiency and recency")
    public List<SkillService.UserSkillView> skills(@PathVariable UUID id) {
        return skillService.skillsOf(OrganizationContext.require(), id);
    }

    @PutMapping("/{id}/skills")
    @Operation(summary = "Add or update one of a user's skills")
    public SkillService.UserSkillView setSkill(@PathVariable UUID id,
                                               @Valid @RequestBody SetUserSkillRequest request) {
        return skillService.setUserSkill(OrganizationContext.require(), id, request.skillId(),
                request.proficiency(), request.yearsExperience(), request.lastUsedAt());
    }

    @DeleteMapping("/{id}/skills/{skillId}")
    @Operation(summary = "Remove one of a user's skills")
    public ResponseEntity<Void> removeSkill(@PathVariable UUID id, @PathVariable UUID skillId) {
        skillService.removeUserSkill(OrganizationContext.require(), id, skillId);
        return ResponseEntity.noContent().build();
    }

    // ---- DTOs ----

    public record UserResponse(UUID id, String email, String fullName, String employeeLevel,
                               UUID departmentId, Set<String> roles, boolean organizationWide,
                               boolean active) {
        static UserResponse from(UserDirectory.UserSummary summary) {
            return new UserResponse(summary.userId(), summary.email(), summary.fullName(),
                    summary.employeeLevel(), summary.departmentId(), summary.roles(),
                    summary.organizationWide(), summary.active());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 255) String fullName,
            @NotNull EmployeeLevel employeeLevel,
            @NotNull BigDecimal weeklyCapacityHours,
            UUID departmentId,
            boolean organizationWide,
            Set<SystemRole> roles) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 255) String fullName,
            @NotNull EmployeeLevel employeeLevel,
            @NotNull BigDecimal weeklyCapacityHours,
            UUID departmentId,
            boolean organizationWide,
            boolean active) {
    }

    public record ReplaceRolesRequest(@NotNull Set<SystemRole> roles) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record SetUserSkillRequest(
            @NotNull UUID skillId,
            @Min(1) @Max(5) int proficiency,
            BigDecimal yearsExperience,
            LocalDate lastUsedAt) {
    }
}
