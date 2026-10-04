package com.trustdesk.common;

import org.springframework.http.HttpStatus;

/** The application's action policy refused a request. {@code policyCode} says why (FINAL_SALE, ADVERSARIAL_TICKET, ...). */
public class GuardrailBlockedException extends ApiException {
    private final String policyCode;

    public GuardrailBlockedException(String policyCode, String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "GUARDRAIL_BLOCKED", reason);
        this.policyCode = policyCode;
    }

    public String getPolicyCode() { return policyCode; }
}
