package com.markbay.subscription_engine.common.exception;

import com.markbay.subscription_engine.common.response.ApiResponse;
import com.markbay.subscription_engine.common.response.ResponseUtil;
import com.markbay.subscription_engine.nomba.exception.NombaApiException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(
            MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Validation error: {}", errors);

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                errors
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(
            ConstraintViolationException ex
    ) {
        Map<String, String> errors = new HashMap<>();

        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(
                    violation.getPropertyPath().toString(),
                    violation.getMessage()
            );
        }

        log.warn("Constraint violation: {}", errors);

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid request parameter",
                errors
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadRequestException(
            BadRequestException ex
    ) {
        log.warn("Bad request: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                "Bad request"
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(
            IllegalArgumentException ex
    ) {
        log.warn("Illegal argument: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                "Illegal argument"
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex
    ) {
        log.warn("Resource not found: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                "Resource not found"
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleConflictException(
            ConflictException ex
    ) {
        log.warn("Conflict: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                "Conflict"
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex
    ) {
        log.error("Database constraint violation: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.CONFLICT.value(),
                "Record already exists or violates a database constraint",
                "Data integrity violation"
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(InvalidCredentialException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidCredentialException(
            InvalidCredentialException ex
    ) {
        log.warn("Invalid credential: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                "Invalid credential"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentialsException(
            BadCredentialsException ex
    ) {
        log.warn("Bad credentials: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid email or password",
                "Authentication failed"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(BadJwtException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadJwtException(
            BadJwtException ex
    ) {
        log.warn("Invalid JWT: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid or expired token",
                "JWT authentication failed"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(
            AccessDeniedException ex
    ) {
        log.warn("Access denied: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.FORBIDDEN.value(),
                "You do not have permission to perform this action",
                "Access denied"
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(NetworkConnectivityException.class)
    public ResponseEntity<ApiResponse<Object>> handleNetworkConnectivityException(
            NetworkConnectivityException ex
    ) {
        log.error("Network connectivity issue: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ex.getMessage(),
                "Network connectivity error"
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidJson(
            HttpMessageNotReadableException ex
    ) {
        log.warn("Invalid request body: {}", ex.getMessage());

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid request body",
                "Request body is missing, malformed, or contains invalid JSON"
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex
    ) {
        String method = ex.getMethod();

        String supportedMethods = ex.getSupportedHttpMethods() == null
                ? "Use the correct HTTP method"
                : ex.getSupportedHttpMethods().toString();

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "HTTP method '" + method + "' is not allowed for this endpoint",
                "Supported method(s): " + supportedMethods
        );

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestParameter(
            MissingServletRequestParameterException ex
    ) {
        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.BAD_REQUEST.value(),
                "Missing required request parameter: " + ex.getParameterName(),
                "Missing request parameter"
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {
        String message = "Invalid value for parameter: " + ex.getName();

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.BAD_REQUEST.value(),
                message,
                "Parameter type mismatch"
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex
    ) {
        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.BAD_REQUEST.value(),
                "Maximum upload size exceeded",
                ex.getMessage()
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(
            Exception ex
    ) {
        log.error("Unexpected server error", ex);

        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Something went wrong. Please try again later.",
                "Internal server error"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResourceFoundException(
            NoResourceFoundException ex
    ) {
        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.NOT_FOUND.value(),
                "Endpoint not found",
                "No resource found for this request path"
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(NombaApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleNombaApiException(
            NombaApiException ex
    ) {
        ApiResponse<Object> errorResponse = ResponseUtil.error(
                HttpStatus.BAD_GATEWAY.value(),
                "Nomba service error",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }
}