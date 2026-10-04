package com.trustdesk.agent;

/** The model's reply could not be parsed or failed validation. Always handled by failing safe. */
public class ModelOutputException extends RuntimeException {
    public ModelOutputException(String message) {
        super(message);
    }
}
