package com.trustdesk.ai;

import java.util.Objects;

/** Trusted instructions and untrusted content are kept in separate fields on purpose. */
public record AiRequest(String systemInstruction, String userContent) {
    public AiRequest {
        Objects.requireNonNull(userContent, "userContent");
    }
}
