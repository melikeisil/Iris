package com.smartwork.common.error;

import java.util.List;
import java.util.UUID;

/** Base for every error the API deliberately produces. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<FieldError> fieldErrors;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of());
    }

    public ApiException(ErrorCode code, String detail, List<FieldError> fieldErrors) {
        super(detail);
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public record FieldError(String field, String code, String message) {
    }

    // ---- Convenience factories for the cases used across every module ----

    /**
     * Used both for genuinely missing resources and for resources the caller may not see.
     * The two are indistinguishable to the client by design.
     */
    public static ApiException notFound(String resource, UUID id) {
        return new ApiException(ErrorCode.NOT_FOUND, resource + " " + id + " was not found.");
    }

    public static ApiException notFound(String resource, String identifier) {
        return new ApiException(ErrorCode.NOT_FOUND, resource + " '" + identifier + "' was not found.");
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(ErrorCode.FORBIDDEN, detail);
    }

    public static ApiException duplicate(String detail) {
        return new ApiException(ErrorCode.DUPLICATE_RESOURCE, detail);
    }

    public static ApiException validation(String detail, List<FieldError> fieldErrors) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, detail, fieldErrors);
    }
}
