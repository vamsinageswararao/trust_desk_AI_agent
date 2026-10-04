package com.trustdesk.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.ai.AiProvider;
import com.trustdesk.guardrails.GuardFlag;
import com.trustdesk.guardrails.InputGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Asks the AI provider to classify a ticket, then treats the reply as untrusted: strict validation, and
 * application floors that can only raise escalation/priority. Malformed or failed model output never propagates;
 * it yields a safe fallback that escalates to a human.
 */
public class TriageEngine {

    private static final Set<String> SENTIMENTS = Set.of("neutral", "frustrated", "worried", "angry", "positive");

    private final AiProvider provider;
    private final ObjectMapper mapper;
    private final PromptBuilder prompts;

    public TriageEngine(AiProvider provider, ObjectMapper mapper) {
        this.provider = provider;
        this.mapper = mapper;
        this.prompts = new PromptBuilder(mapper);
    }

    public TriageDecision triage(TicketContext ctx, Set<GuardFlag> flags) {
        TriageDecision base;
        try {
            String raw = provider.complete(prompts.triage(ctx)).content();
            base = parse(raw);
        } catch (ModelOutputException e) {
            base = fallback("invalid_model_output: " + e.getMessage());
        } catch (RuntimeException e) {
            base = fallback("ai_provider_error: " + e.getClass().getSimpleName());
        }
        return applyFloors(base, flags);
    }

    private TriageDecision parse(String raw) {
        JsonNode n = ModelJson.parseObject(mapper, raw);
        String categoryText = ModelJson.requiredText(n, "category");
        String priorityText = ModelJson.requiredText(n, "priority");
        TicketCategory category = TicketCategory.parse(categoryText)
                .orElseThrow(() -> new ModelOutputException("unsupported category"));
        TicketPriority priority = TicketPriority.parse(priorityText)
                .orElseThrow(() -> new ModelOutputException("unsupported priority"));
        boolean escalate = ModelJson.requiredBoolean(n, "should_escalate");
        List<String> warnings = new ArrayList<>();
        String sentiment = n.hasNonNull("sentiment") && n.get("sentiment").isTextual()
                ? n.get("sentiment").asText().strip().toLowerCase() : "neutral";
        if (!SENTIMENTS.contains(sentiment)) {
            warnings.add("unsupported sentiment coerced to neutral");
            sentiment = "neutral";
        }
        String reason = n.hasNonNull("reason_summary") && n.get("reason_summary").isTextual()
                ? ModelJson.clean(n.get("reason_summary").asText(), 300) : "";
        return new TriageDecision(category, priority, sentiment, escalate, reason, "model", warnings);
    }

    private static TriageDecision fallback(String why) {
        return new TriageDecision(TicketCategory.GENERAL, TicketPriority.MEDIUM, "neutral", true,
                "AI triage unavailable or invalid; escalated to a human for review.", "fallback", List.of(why));
    }

    /** Application floors: flags can only make the outcome safer. */
    static TriageDecision applyFloors(TriageDecision d, Set<GuardFlag> flags) {
        TicketPriority priority = d.priority();
        boolean escalate = d.shouldEscalate();
        List<String> warnings = new ArrayList<>(d.warnings());
        if (flags.contains(GuardFlag.SAFETY_ISSUE)) {
            if (priority != TicketPriority.URGENT || !escalate) warnings.add("floor: safety issue -> urgent + escalate");
            priority = TicketPriority.URGENT;
            escalate = true;
        }
        if (InputGuard.isAdversarial(flags)) {
            TicketPriority floor = flags.contains(GuardFlag.SECRET_EXTRACTION) || flags.contains(GuardFlag.IDENTITY_BYPASS)
                    ? TicketPriority.HIGH : TicketPriority.MEDIUM;
            if (!escalate || priority.ordinal() < floor.ordinal()) warnings.add("floor: manipulation attempt -> escalate");
            priority = TicketPriority.max(priority, floor);
            escalate = true;
        }
        return new TriageDecision(d.category(), priority, d.sentiment(), escalate, d.reasonSummary(), d.source(),
                List.copyOf(warnings));
    }
}
