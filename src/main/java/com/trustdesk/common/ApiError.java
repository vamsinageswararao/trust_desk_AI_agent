package com.trustdesk.common;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Consistent error body for every API failure. Never contains stack traces or internal details. */
public record ApiError(String timestamp, int status, String error, String code, String message,
                       String path, List<FieldViolation> details) {

    public record FieldViolation(String field, String message) {}

    public static ApiError of(HttpStatus status, String code, String message, String path,
                              List<FieldViolation> details) {
        return new ApiError(Instant.now().toString(), status.value(), status.getReasonPhrase(), code, message,
                path, details == null ? List.of() : details);
    }
}
