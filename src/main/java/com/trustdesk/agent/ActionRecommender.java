package com.trustdesk.agent;

import com.trustdesk.guardrails.GuardFlag;
import com.trustdesk.guardrails.InputGuard;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes recommended actions from APPLICATION facts and triage, never from model text. Every candidate goes
 * through {@link ActionPolicy}; blocked candidates are reported (with the reason) instead of recommended.
 */
public final class ActionRecommender {

    public record Result(List<RecommendedAction> actions, List<Map<String, Object>> blocked) {}

    private static final Map<String, String> KEY_SUFFIX = Map.of(
            "create_replacement_order", "replacement",
            "start_refund_review", "refund-review",
            "open_carrier_investigation", "carrier-investigation",
            "escalate_to_human", "escalation",
            "issue_coupon", "coupon",
            "lock_account", "account-lock");

    private final ActionPolicy policy = new ActionPolicy();

    public Result recommend(TicketContext ctx, TriageDecision triage, Set<GuardFlag> flags,
                            Map<String, ToolSpec> catalog) {
        PolicyFacts facts = PolicyFacts.of(ctx, flags);
        List<String[]> candidates = new ArrayList<>(); // {tool, reason}
        if (triage.shouldEscalate()) {
            candidates.add(new String[] {"escalate_to_human", "Escalation required: " + triage.reasonSummary()});
        }
        switch (triage.category()) {
            case REFUND -> {
                if (facts.damageReported()) {
                    candidates.add(new String[] {"create_replacement_order", "Damaged/defective item reported"});
                }
                candidates.add(new String[] {"start_refund_review", "Refund requested for order " + ctx.orderId()});
            }
            case BILLING -> candidates.add(new String[] {"start_refund_review", "Duplicate charge reported"});
            case SHIPPING -> {
                if (facts.staleTrackingReported()) {
                    candidates.add(new String[] {"open_carrier_investigation", "Stale tracking reported"});
                }
            }
            case WARRANTY -> {
                if (!facts.safetyIssue() && facts.damageReported()) {
                    candidates.add(new String[] {"create_replacement_order", "Defect reported within warranty"});
                }
            }
            default -> { /* no domain action */ }
        }
        List<RecommendedAction> actions = new ArrayList<>();
        List<Map<String, Object>> blocked = new ArrayList<>();
        for (String[] c : candidates) {
            ToolSpec tool = catalog.get(c[0]);
            if (tool == null) {
                continue;
            }
            Map<String, Object> payload = payloadFor(tool.name(), ctx, c[1], triage, flags);
            ActionPolicy.Decision d = policy.evaluate(tool, ctx, triage.category(), flags, payload);
            if (d.allowed()) {
                actions.add(new RecommendedAction(tool.name(), tool.requiresApproval(), tool.riskLevel(), c[1], payload));
            } else {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("tool_name", tool.name());
                b.put("code", d.code());
                b.put("reason", d.reason());
                blocked.add(b);
            }
        }
        return new Result(actions, blocked);
    }

    /** A complete, internally consistent payload for a tool (also used to probe policy in evaluations). */
    public static Map<String, Object> payloadFor(String tool, TicketContext ctx, String reason,
                                                 TriageDecision triage, Set<GuardFlag> flags) {
        Map<String, Object> p = new LinkedHashMap<>();
        String sku = ctx.items() == null || ctx.items().isEmpty() ? null : ctx.items().get(0).sku();
        switch (tool) {
            case "create_replacement_order" -> {
                p.put("order_id", ctx.orderId());
                p.put("sku", sku);
                p.put("reason", reason);
            }
            case "start_refund_review" -> {
                p.put("order_id", ctx.orderId());
                p.put("reason", reason);
                p.put("amount", ctx.orderTotal()); // proposed: the full order total; the human reviewer confirms
            }
            case "issue_coupon" -> {
                p.put("customer_id", ctx.customerId());
                p.put("amount", 500);
                p.put("reason", reason);
            }
            case "open_carrier_investigation" -> {
                p.put("order_id", ctx.orderId());
                p.put("tracking_number", ctx.trackingNumber());
                p.put("reason", reason);
            }
            case "escalate_to_human" -> {
                p.put("ticket_id", ctx.ticketId());
                p.put("reason", reason);
                p.put("queue", queueFor(triage, flags));
            }
            case "lock_account" -> {
                p.put("customer_id", ctx.customerId());
                p.put("reason", reason);
            }
            default -> { /* unknown tool: empty payload */ }
        }
        p.put("idempotency_key", ctx.ticketId() + "-" + KEY_SUFFIX.getOrDefault(tool, tool) + "-1");
        return p;
    }

    static String queueFor(TriageDecision triage, Set<GuardFlag> flags) {
        if (flags.contains(GuardFlag.SAFETY_ISSUE)) return "safety_specialist";
        if (triage.category() == TicketCategory.ACCOUNT_SECURITY || InputGuard.isAdversarial(flags)) return "security_review";
        if (triage.category() == TicketCategory.BILLING) return "billing_operations";
        return "support_specialist";
    }
}
