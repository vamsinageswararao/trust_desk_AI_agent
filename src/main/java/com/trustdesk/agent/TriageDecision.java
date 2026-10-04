package com.trustdesk.agent;

import java.util.List;

/** Validated triage result. {@code source} is "model" or "fallback"; {@code warnings} explain overrides/fallbacks. */
public record TriageDecision(TicketCategory category, TicketPriority priority, String sentiment,
                             boolean shouldEscalate, String reasonSummary, String source, List<String> warnings) {}
