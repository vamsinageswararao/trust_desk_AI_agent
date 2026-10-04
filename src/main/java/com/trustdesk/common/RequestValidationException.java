package com.trustdesk.common;

/** A request failed a rule that Bean Validation cannot express statically (for example a configured maximum). */
public class RequestValidationException extends RuntimeException {
    private final String field;

    public RequestValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
