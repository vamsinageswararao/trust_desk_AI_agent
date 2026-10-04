package com.trustdesk.agent;

import java.util.Locale;
import java.util.Optional;

public enum TicketPriority {
    LOW, MEDIUM, HIGH, URGENT;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TicketPriority max(TicketPriority a, TicketPriority b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public static Optional<TicketPriority> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.strip().toUpperCase(Locale.ROOT);
        for (TicketPriority p : values()) {
            if (p.name().equals(v)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
