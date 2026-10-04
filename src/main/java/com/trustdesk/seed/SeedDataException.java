package com.trustdesk.seed;

public class SeedDataException extends RuntimeException {
    public SeedDataException(String message) {
        super(message);
    }

    public SeedDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
