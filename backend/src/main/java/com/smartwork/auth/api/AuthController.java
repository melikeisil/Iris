package com.smartwork.auth.api;

import com.smartwork.auth.application.AuthService;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.identity.application.UserDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth", description = "Authentication and session lifecycle")
public class AuthController {

    private final AuthService authService;
    private final UserDirectory users;
    private final CurrentUserAssembler assembler;

    public AuthController(AuthService authService, UserDirectory users, CurrentUserAssembler assembler) {
        this.authService = authService;
        this.users = users;
        this.assembler = assembler;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange credentials for an access and refresh token pair")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS or ACCOUNT_DISABLED")
    })
    public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ResponseEntity.ok(toResponse(authService.login(request.email(), request.password())));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Rotate a refresh token",
            description = "Refresh tokens are single-use. Presenting one that has already been "
                    + "consumed revokes every token in its family and returns REFRESH_TOKEN_REUSED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or REFRESH_TOKEN_REUSED")
    })
    public ResponseEntity<AuthDtos.TokenResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return ResponseEntity.ok(toResponse(authService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Revoke the refresh token family behind this session")
    public ResponseEntity<Void> logout(@Valid @RequestBody AuthDtos.LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "The authenticated principal, its scope and coarse capabilities")
    public ResponseEntity<AuthDtos.CurrentUserResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required.");
        }
        UserDirectory.UserSummary summary = users
                .findSummary(principal.organizationId(), principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "The account no longer exists."));

        return ResponseEntity.ok(assembler.assemble(principal, summary.fullName(), summary.email()));
    }

    private AuthDtos.TokenResponse toResponse(AuthService.AuthResult result) {
        return new AuthDtos.TokenResponse(
                result.accessToken().token(),
                result.refreshToken().token(),
                result.accessToken().expiresInSeconds(),
                "Bearer",
                assembler.assemble(result.principal(), result.fullName(), result.email()));
    }
}
