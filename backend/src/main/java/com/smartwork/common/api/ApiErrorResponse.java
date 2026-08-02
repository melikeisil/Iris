package com.smartwork.common.api;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * RFC 7807 problem detail, extended with a stable {@code code} and a correlation ID.
 * Serialized as {@code application/problem+json}.
 */
@Schema(name = "ApiError", description = "RFC 7807 problem detail")
public record ApiErrorResponse(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String instance,
        String correlationId,
        Instant timestamp,
        List<FieldErrorResponse> errors) {

    public record FieldErrorResponse(String field, String code, String message) {
    }

    public static ApiErrorResponse of(ErrorCode code, String detail, String instance, String correlationId) {
        return new ApiErrorResponse(
                code.type(), code.title(), code.status().value(), code.name(),
                detail, instance, correlationId, Instant.now(), List.of());
    }

    public static ApiErrorResponse of(ApiException exception, String instance, String correlationId) {
        ErrorCode code = exception.getCode();
        List<FieldErrorResponse> errors = exception.getFieldErrors().stream()
                .map(fieldError -> new FieldErrorResponse(
                        fieldError.field(), fieldError.code(), fieldError.message()))
                .toList();
        return new ApiErrorResponse(
                code.type(), code.title(), code.status().value(), code.name(),
                exception.getMessage(), instance, correlationId, Instant.now(), errors);
    }
}
