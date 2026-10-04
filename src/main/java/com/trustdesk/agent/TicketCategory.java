package com.trustdesk.agent;

import java.util.Locale;
import java.util.Optional;

public enum TicketCategory {
    SHIPPING, REFUND, WARRANTY, BILLING, ACCOUNT_SECURITY, GENERAL;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<TicketCategory> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(Locale.ROOT);
        for (TicketCategory c : values()) {
            if (c.name().equals(v)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
