package com.trustdesk.guardrails;

/** Signals raised by {@link InputGuard} about untrusted ticket text. */
public enum GuardFlag {
    PROMPT_INJECTION, SECRET_EXTRACTION, IDENTITY_BYPASS, HIDDEN_ACTION_REQUEST, COUPON_REQUEST, SAFETY_ISSUE
}
