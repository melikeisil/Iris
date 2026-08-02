package com.smartwork.common.api;

import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

/**
 * Turns every exception into a problem+json response with a stable error code.
 *
 * <p>Internal failures never leak a message or a stack trace to the client: the client
 * gets a correlation ID, and the detail goes to the log under that ID.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException exception, HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(exception, request.getRequestURI(), CorrelationId.current());
        if (exception.getCode().status().is5xxServerError()) {
            log.error("API error {}: {}", exception.getCode(), exception.getMessage(), exception);
        } else {
            log.debug("API error {}: {}", exception.getCode(), exception.getMessage());
        }
        return respond(exception.getCode(), body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {

        List<ApiErrorResponse.FieldErrorResponse> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiErrorResponse.FieldErrorResponse(
                        fieldError.getField(),
                        fieldError.getCode() == null ? "INVALID" : fieldError.getCode().toUpperCase(),
                        fieldError.getDefaultMessage()))
                .toList();

        ApiErrorResponse body = new ApiErrorResponse(
                ErrorCode.VALIDATION_FAILED.type(), ErrorCode.VALIDATION_FAILED.title(),
                ErrorCode.VALIDATION_FAILED.status().value(), ErrorCode.VALIDATION_FAILED.name(),
                "One or more fields are invalid.", request.getRequestURI(),
                CorrelationId.current(), java.time.Instant.now(), errors);

        return respond(ErrorCode.VALIDATION_FAILED, body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {

        List<ApiErrorResponse.FieldErrorResponse> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiErrorResponse.FieldErrorResponse(
                        violation.getPropertyPath().toString(), "INVALID", violation.getMessage()))
                .toList();

        ApiErrorResponse body = new ApiErrorResponse(
                ErrorCode.VALIDATION_FAILED.type(), ErrorCode.VALIDATION_FAILED.title(),
                ErrorCode.VALIDATION_FAILED.status().value(), ErrorCode.VALIDATION_FAILED.name(),
                "One or more parameters are invalid.", request.getRequestURI(),
                CorrelationId.current(), java.time.Instant.now(), errors);

        return respond(ErrorCode.VALIDATION_FAILED, body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformed(Exception exception, HttpServletRequest request) {
        log.debug("Malformed request to {}: {}", request.getRequestURI(), exception.getMessage());
        return simple(ErrorCode.MALFORMED_REQUEST,
                "The request body or parameters could not be read.", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(
            OptimisticLockingFailureException exception, HttpServletRequest request) {
        return simple(ErrorCode.CONCURRENT_MODIFICATION,
                "This resource was modified by someone else. Reload it and try again.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        // The constraint name would tell an attacker about the schema, so it is logged, not returned.
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(),
                exception.getMostSpecificCause().getMessage());
        return simple(ErrorCode.DUPLICATE_RESOURCE,
                "The operation conflicts with existing data.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        return simple(ErrorCode.FORBIDDEN, "You do not have permission to perform this action.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException exception, HttpServletRequest request) {
        return simple(ErrorCode.UNAUTHENTICATED, "Authentication is required.", request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoHandler(
            NoHandlerFoundException exception, HttpServletRequest request) {
        return simple(ErrorCode.NOT_FOUND, "No endpoint matches this request.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception on {} [correlationId={}]",
                request.getRequestURI(), CorrelationId.current(), exception);
        return simple(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Quote the correlation ID when reporting it.", request);
    }

    private ResponseEntity<ApiErrorResponse> simple(ErrorCode code, String detail, HttpServletRequest request) {
        return respond(code, ApiErrorResponse.of(code, detail, request.getRequestURI(), CorrelationId.current()));
    }

    private ResponseEntity<ApiErrorResponse> respond(ErrorCode code, ApiErrorResponse body) {
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
