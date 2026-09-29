package com.sosid.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

public final class ApiSupport {
    private ApiSupport() {
    }

    public static UUID currentUserId() {
        Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (p instanceof UUID id) return id;
        throw new ForbiddenException();
    }

    public static String correlationId(HttpServletRequest request) {
        String value = request.getHeader("X-Correlation-Id");
        return value == null || value.length() > 100 ? UUID.randomUUID().toString() : value;
    }

    public static final class NotFoundException extends RuntimeException {
    }

    public static final class ForbiddenException extends RuntimeException {
    }

    public static final class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }

    public record ErrorResponse(String code, String message, String correlationId, Instant timestamp) {
    }

    @RestControllerAdvice
    public static class Errors {
        @ExceptionHandler(NotFoundException.class)
        ResponseEntity<ErrorResponse> notFound(HttpServletRequest r) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", r);
        }

        @ExceptionHandler({ForbiddenException.class})
        ResponseEntity<ErrorResponse> forbidden(HttpServletRequest r) {
            return error(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE", r);
        }

        @ExceptionHandler(InvalidRequestException.class)
        ResponseEntity<ErrorResponse> invalid(HttpServletRequest r) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", r);
        }

        @ExceptionHandler(Exception.class)
        ResponseEntity<ErrorResponse> unexpected(HttpServletRequest r) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", r);
        }

        private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, HttpServletRequest r) {
            return ResponseEntity.status(status).body(new ErrorResponse(code, code.equals("INVALID_REQUEST") ? "Request is invalid" : "Request could not be completed", correlationId(r), Instant.now()));
        }
    }
}
