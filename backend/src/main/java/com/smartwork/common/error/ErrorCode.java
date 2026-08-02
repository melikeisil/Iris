package com.smartwork.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes. The {@code code} is the API contract; the title
 * and detail are human text and may change without notice.
 */
public enum ErrorCode {

    // 400
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    LINK_CYCLE_DETECTED(HttpStatus.BAD_REQUEST, "Link would create a cycle"),
    INVALID_LINK_TYPE(HttpStatus.BAD_REQUEST, "Link type not permitted between these items"),
    MALFORMED_FILTER(HttpStatus.BAD_REQUEST, "Malformed filter"),

    // 401
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected"),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "Account disabled"),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "Operation not permitted"),
    TRANSITION_NOT_PERMITTED(HttpStatus.FORBIDDEN, "Workflow transition not permitted"),
    ROUTE_NOT_PERMITTED(HttpStatus.FORBIDDEN, "Approval route not permitted for this actor"),
    LEVEL_RESTRICTION_VIOLATED(HttpStatus.FORBIDDEN, "Employee level restriction violated"),

    // 404 - also returned for resources the caller may not see, so that a 403 never
    // confirms the existence of something outside the caller's scope.
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),

    // 409
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "The resource changed while you were editing it"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists"),
    ALREADY_DECIDED(HttpStatus.CONFLICT, "This request has already been decided"),

    // 422
    INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid workflow transition"),
    TRANSITION_GUARD_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Workflow guard failed"),
    RECOMMENDATION_STALE(HttpStatus.UNPROCESSABLE_ENTITY, "Recommendation is stale"),
    SPRINT_CAPACITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Sprint capacity exceeded"),
    TYPE_RULE_VIOLATED(HttpStatus.UNPROCESSABLE_ENTITY, "Work item type rule violated"),

    // 429
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    // 5xx
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"),
    AI_SERVICE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI service unavailable"),
    JIRA_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Jira integration is not configured"),
    JIRA_AUTHENTICATION_FAILED(HttpStatus.BAD_GATEWAY, "Jira authentication failed"),
    JIRA_INVALID_CONFIGURATION(HttpStatus.BAD_GATEWAY, "Jira configuration is invalid"),
    JIRA_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "Jira unavailable");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String type() {
        return "https://smartwork.ai/errors/" + name().toLowerCase().replace('_', '-');
    }
}
