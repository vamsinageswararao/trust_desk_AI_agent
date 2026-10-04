package com.trustdesk.agent;

import com.trustdesk.guardrails.GuardFlag;
import com.trustdesk.guardrails.InputGuard;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * The application's authority over tool use. Decides whether a tool action may even be REQUESTED for a ticket.
 * It is applied to AI recommendations and to human/UI requests alike; model text can never bypass it.
 * (Approval and execution are separate gates enforced by the tool-action service.)
 */
public final class ActionPolicy {

    public record Decision(boolean allowed, String code, String reason) {
        public static Decision ok() {
            return new Decision(true, "OK", "allowed");
        }

        public static Decision blocked(String code, String reason) {
            return new Decision(false, code, reason);
        }
    }

    public Decision evaluate(ToolSpec tool, TicketContext ctx, TicketCategory category, Set<GuardFlag> flags,
                             Map<String, Object> payload) {
        if (category == null) {
            return Decision.blocked("NOT_TRIAGED", "Ticket must be triaged before any action is requested");
        }
        if (!tool.allowedCategories().contains(category.wire())) {
            return Decision.blocked("CATEGORY_NOT_ALLOWED",
                    tool.name() + " is not permitted for category '" + category.wire() + "'");
        }
        for (String field : tool.requiredFields()) {
            Object v = payload == null ? null : payload.get(field);
            if (v == null || v.toString().isBlank()) {
                return Decision.blocked("MISSING_FIELD", "Required field missing: " + field);
            }
        }
        Decision mismatch = checkConsistency(ctx, payload);
        if (!mismatch.allowed()) {
            return mismatch;
        }
        if (InputGuard.isAdversarial(flags) && !"escalate_to_human".equals(tool.name())) {
            return Decision.blocked("ADVERSARIAL_TICKET",
                    "Ticket contains manipulation attempts (" + flags + "); only escalation is allowed");
        }
        PolicyFacts facts = PolicyFacts.of(ctx, flags);
        switch (tool.name()) {
            case "start_refund_review" -> {
                Decision amount = amountWithin(payload, BigDecimal.valueOf(ctx.orderTotal()), "INVALID_AMOUNT",
                        "Refund amount must be > 0 and not exceed the order total");
                if (!amount.allowed()) return amount;
                if (facts.allItemsNonReturnable()) {
                    return Decision.blocked("FINAL_SALE", "Order contains only non-returnable/final-sale items (KB-REFUND-001)");
                }
                if (category == TicketCategory.REFUND && !facts.returnWindowOpen()) {
                    return Decision.blocked("RETURN_WINDOW_CLOSED",
                            "Ticket was created after the return window (evaluated from ticket created_at)");
                }
            }
            case "create_replacement_order" -> {
                if (facts.allItemsNonReturnable()) {
                    return Decision.blocked("FINAL_SALE", "Order contains only non-returnable/final-sale items");
                }
                if (facts.safetyIssue()) {
                    return Decision.blocked("SAFETY_ESCALATION_FIRST",
                            "Safety issues must be escalated to a specialist before any replacement (KB-WARRANTY-001)");
                }
                if (category == TicketCategory.REFUND && !facts.returnWindowOpen()) {
                    return Decision.blocked("RETURN_WINDOW_CLOSED", "Ticket was created after the return window");
                }
                if (category == TicketCategory.WARRANTY && !facts.warrantyCovered()) {
                    return Decision.blocked("WARRANTY_NOT_COVERED", "Delivery date is outside the warranty period");
                }
            }
            case "issue_coupon" -> {
                BigDecimal max = BigDecimal.valueOf(tool.maxAmountInr() == null ? 1000 : tool.maxAmountInr());
                Decision amount = amountWithin(payload, max, "COUPON_LIMIT",
                        "Coupon amount must be > 0 and at most INR " + max.toPlainString() + " (KB-COUPON-001)");
                if (!amount.allowed()) return amount;
            }
            case "lock_account" -> {
                return Decision.blocked("NOT_AUTOMATABLE",
                        "Account lock cannot be requested automatically; it needs a verified security process");
            }
            default -> { /* open_carrier_investigation, escalate_to_human: category + consistency checks suffice */ }
        }
        return Decision.ok();
    }

    private static Decision checkConsistency(TicketContext ctx, Map<String, Object> p) {
        if (p == null) {
            return Decision.ok();
        }
        if (differs(p.get("order_id"), ctx.orderId())) {
            return Decision.blocked("ORDER_MISMATCH", "order_id does not belong to this ticket");
        }
        if (differs(p.get("customer_id"), ctx.customerId())) {
            return Decision.blocked("CUSTOMER_MISMATCH", "customer_id does not belong to this ticket");
        }
        if (differs(p.get("tracking_number"), ctx.trackingNumber())) {
            return Decision.blocked("ORDER_MISMATCH", "tracking_number does not belong to this ticket's order");
        }
        if (differs(p.get("ticket_id"), ctx.ticketId())) {
            return Decision.blocked("TICKET_MISMATCH", "ticket_id does not match the ticket");
        }
        return Decision.ok();
    }

    private static boolean differs(Object supplied, String actual) {
        return supplied != null && !supplied.toString().isBlank() && !supplied.toString().equals(actual);
    }

    private static Decision amountWithin(Map<String, Object> payload, BigDecimal max, String code, String message) {
        Object raw = payload == null ? null : payload.get("amount");
        try {
            BigDecimal amount = raw instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(String.valueOf(raw).strip());
            if (amount.signum() > 0 && amount.compareTo(max) <= 0) {
                return Decision.ok();
            }
        } catch (NumberFormatException ignored) {
            // fall through to blocked
        }
        return Decision.blocked(code, message);
    }
}
