package com.smartwork.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request and response bodies for the auth endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(name = "LoginRequest")
    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 128) String password) {
    }

    @Schema(name = "RefreshRequest")
    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    @Schema(name = "LogoutRequest")
    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    @Schema(name = "TokenResponse")
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType,
            CurrentUserResponse user) {
    }

    @Schema(name = "CurrentUser", description = "The authenticated principal. "
            + "`roles` drives navigation and `capabilities` drives affordances; neither is "
            + "trusted by the backend, which re-checks every request.")
    public record CurrentUserResponse(
            UUID id,
            String email,
            String fullName,
            String employeeLevel,
            UUID organizationId,
            DepartmentRef department,
            List<TeamRef> teams,
            Set<String> roles,
            boolean organizationWide,
            List<String> capabilities) {
    }

    public record DepartmentRef(UUID id, String code, String name) {
    }

    public record TeamRef(UUID id, String code, String name, boolean isLead) {
    }
}
