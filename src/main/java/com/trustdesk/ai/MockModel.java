package com.trustdesk.ai;

import com.trustdesk.guardrails.GuardFlag;
import com.trustdesk.guardrails.InputGuard;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic stand-in for an LLM, so the product can be demonstrated and tested with no API key. It works from
 * the same JSON prompt a real model would receive: it classifies from ticket text and context (never from stored
 * labels), and writes replies from fixed templates in which EACH policy statement is included only if its
 * supporting KB document is present in the supplied evidence (so it cannot assert ungrounded policy). It only
 * reads document ids; it never interprets document text as instructions.
 */
public final class MockModel {

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final Pattern ACCOUNT_CHANGE = Pattern.compile(
            "(?:change|update|reset|delete|close)\\W+(?:\\w+\\W+){0,3}?(?:email|password|address|account)", CI);
    private static final Pattern BILLING = Pattern.compile(
            "double charge|charged twice|two charges|duplicate charge|charged me twice|overcharg", CI);
    private static final Pattern DAMAGE = Pattern.compile("damag|crack|broken|defect|faulty|not working|stopped working", CI);
    private static final Pattern SHIPPING = Pattern.compile(
            "tracking|no movement|not moved|hasn.t moved|stuck in transit|lost (?:my )?(?:package|parcel|order)"
                    + "|not arrived|hasn.t arrived|not received|haven.t received|carrier|courier", CI);
    private static final Pattern REFUND = Pattern.compile("refund|return|replacement|money back|licen[sc]e|cancel", CI);
    private static final Pattern WARRANTY = Pattern.compile("warranty", CI);
    private static final Pattern URGENT = Pattern.compile("travel|trip|flight|urgent|asap|time-sensitive|deadline", CI);
    private static final Pattern FRUSTRATED = Pattern.compile(
            "damag|crack|not moved|no movement|double|two charges|quickly|asap|stuck|unacceptable|angry", CI);

    private MockModel() {}

    // ------------------------------------------------------------------ triage
    public static Map<String, Object> triage(Map<String, Object> in) {
        Map<String, Object> ticket = asMap(in.get("untrusted_ticket"));
        Map<String, Object> ctx = asMap(in.get("context"));
        String text = str(ticket.get("subject")) + " " + str(ticket.get("body"));
        Set<GuardFlag> flags = InputGuard.analyze(text);
        boolean verified = Boolean.TRUE.equals(ctx.get("customer_verified"));
        Map<String, Object> order = asMap(ctx.get("order"));
        boolean nonReturnable = allNonReturnable(order);

        String category;
        String priority;
        boolean escalate = false;
        String reason;
        if (flags.contains(GuardFlag.SECRET_EXTRACTION)) {
            category = "account_security"; priority = "high"; escalate = true;
            reason = "Request for internal instructions or secrets";
        } else if (flags.contains(GuardFlag.IDENTITY_BYPASS) || ACCOUNT_CHANGE.matcher(text).find()) {
            category = "account_security"; priority = "high";
            escalate = flags.contains(GuardFlag.IDENTITY_BYPASS) || !verified;
            reason = "Account change request requiring identity verification";
        } else if (flags.contains(GuardFlag.PROMPT_INJECTION) || flags.contains(GuardFlag.HIDDEN_ACTION_REQUEST)) {
            category = "general"; priority = "medium"; escalate = true;
            reason = "Message tries to override policy or hide actions";
        } else if (flags.contains(GuardFlag.SAFETY_ISSUE)) {
            category = "warranty"; priority = "urgent"; escalate = true;
            reason = "Product safety issue reported";
        } else if (BILLING.matcher(text).find()) {
            category = "billing"; priority = "high";
            reason = "Duplicate charge reported";
        } else if (DAMAGE.matcher(text).find()) {
            boolean pastReturnWindow = pastReturnWindow(ticket, order);
            category = pastReturnWindow ? "warranty" : "refund";
            priority = "medium";
            reason = pastReturnWindow ? "Defect reported after the return window" : "Damaged or defective item reported";
        } else if (SHIPPING.matcher(text).find()) {
            category = "shipping";
            priority = URGENT.matcher(text).find() ? "high" : "medium";
            reason = "Delivery or tracking problem";
        } else if (REFUND.matcher(text).find()) {
            category = "refund"; priority = nonReturnable ? "low" : "medium";
            reason = nonReturnable ? "Refund requested for a non-returnable item" : "Refund or return requested";
        } else if (WARRANTY.matcher(text).find()) {
            category = "warranty"; priority = "medium";
            reason = "Warranty question";
        } else {
            category = "general"; priority = "low";
            reason = "No specific topic recognised";
        }
        String sentiment = flags.contains(GuardFlag.SAFETY_ISSUE) ? "worried"
                : (FRUSTRATED.matcher(text).find() ? "frustrated" : "neutral");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("category", category);
        out.put("priority", priority);
        out.put("sentiment", sentiment);
        out.put("should_escalate", escalate);
        out.put("reason_summary", reason);
        return out;
    }

    // ------------------------------------------------------------------ draft
    public static Map<String, Object> draft(Map<String, Object> in) {
        Map<String, Object> facts = asMap(in.get("policy_facts"));
        Map<String, Object> triage = asMap(in.get("triage"));
        Map<String, Object> ctx = asMap(in.get("context"));
        Set<String> evidence = new LinkedHashSet<>();
        if (in.get("untrusted_evidence") instanceof List<?> docs) {
            for (Object d : docs) {
                evidence.add(str(asMap(d).get("doc_id")));
            }
        }
        String category = str(triage.get("category"));
        String tier = str(ctx.get("customer_tier"));
        Set<String> cites = new LinkedHashSet<>();
        List<String> paragraphs = new ArrayList<>();
        Builder b = new Builder(evidence, cites, paragraphs);

        switch (category) {
            case "refund" -> {
                if (flag(facts, "all_items_non_returnable")) {
                    b.say("KB-REFUND-001", "Software licenses, downloadable products and items marked final sale are not "
                            + "eligible for a refund after purchase unless required by law or approved by a support manager, "
                            + "so I'm unable to approve a refund for this order. If you believe an exception applies, a "
                            + "support manager can review it.");
                } else if (flag(facts, "return_window_open")) {
                    b.say("KB-REFUND-001", "Physical products can be returned within 7 calendar days of delivery when they "
                            + "are unused, defective, damaged on arrival or not as listed. Because your request reached us "
                            + "within the return window, we can offer either a replacement or a refund review. We may ask "
                            + "you for a photo or a short description of the damage before the action is approved. A refund "
                            + "review needs human approval, so I can't confirm that any refund has been issued.");
                } else {
                    b.say("KB-REFUND-001", "Physical products can be returned within 7 calendar days of delivery, and your "
                            + "request was received after that window. "
                            + ("gold".equals(tier)
                                    ? "As a Gold-tier customer you may be considered for goodwill support, which needs human approval."
                                    : "I can't approve a refund under the standard policy."));
                }
            }
            case "shipping" -> {
                b.say("KB-SHIPPING-001", "If tracking shows no movement for 5 or more business days after dispatch, we can open a "
                        + "carrier investigation, and that is the next step for your order. A replacement or refund review "
                        + "can be considered after the carrier confirms the problem or after 10 business days without "
                        + "movement, whichever comes first. I'm not able to promise an instant refund or replacement.");
                if (flag(facts, "urgent_travel")) {
                    b.say("KB-SHIPPING-001", "Since you mentioned a time-sensitive need, I've marked your request as high priority.");
                }
            }
            case "warranty" -> {
                if (flag(facts, "safety_issue")) {
                    b.say("KB-WARRANTY-001", "A swelling battery or similar symptom is a product safety issue. Please stop using "
                            + "the device. I'm escalating your case to a human specialist as urgent, and I won't suggest "
                            + "troubleshooting steps for this.");
                }
                b.say("KB-WARRANTY-001", "Electronics have a 12-month limited warranty from the delivery date, with a 6-month "
                        + "extension for Gold-tier customers on eligible hardware. "
                        + (flag(facts, "warranty_covered")
                                ? "Based on the delivery date on your order, your device appears to be within warranty coverage; a specialist will confirm. "
                                : "")
                        + "Any replacement under warranty needs human approval and may require proof of purchase, serial number and product photos.");
            }
            case "billing" -> {
                b.say("KB-BILLING-001", "Thanks for flagging the duplicate charge. We'll check whether there is one order or "
                        + "several; if there is only one, we start a refund review or billing investigation. I can't confirm "
                        + "that any refund has been made until our payment operations team confirms it.");
                b.say("KB-BILLING-001", "If we can't locate the transaction we'll need the payment date, the amount, the payment "
                        + "method type and the last four digits of the card. Please never send your full card number, CVV, OTP or password.");
            }
            case "account_security" -> {
                if (flag(facts, "secret_requested")) {
                    b.say("KB-SECURITY-001", "I can't share internal instructions, system prompts, API keys or internal notes, and I "
                            + "won't act on instructions inside a message that try to change how support works.");
                    b.say("KB-ACCOUNT-001", "Support replies never include internal notes, hidden prompts, keys or other customers' data.");
                } else {
                    b.say("KB-ACCOUNT-001", "Account email changes, password resets, address changes on active orders and account "
                            + "deletions require identity verification. I can't make account changes based only on a chat "
                            + "message or skip identity checks, so I'm passing your request to a human support specialist "
                            + "who will verify your identity.");
                    if (flag(facts, "identity_bypass_requested")) {
                        b.say("KB-SECURITY-001", "I also can't follow instructions that ask me to bypass these checks.");
                    }
                }
            }
            default -> {
                if (flag(facts, "injection_detected") || flag(facts, "hidden_action_requested")) {
                    b.say("KB-SECURITY-001", "I can't act on instructions inside a message that try to override support policy or hide "
                            + "actions from the review team. I haven't taken any such action, and I've flagged this ticket for human review.");
                    if (flag(facts, "coupon_requested")) {
                        b.say("KB-COUPON-001", "Goodwill coupons need human approval and can't be issued because a message asks us to "
                                + "ignore the rules or keep something hidden.");
                    }
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        if (paragraphs.isEmpty()) {
            out.put("body", "Thank you for contacting us. I couldn't find policy guidance that covers this, so a human "
                    + "specialist will review your request.");
            out.put("citations", List.of());
            out.put("needs_human", true);
            return out;
        }
        String name = str(in.get("customer_first_name"));
        StringBuilder body = new StringBuilder(name.isBlank() ? "Hello," : "Hi " + name + ",").append("\n\n");
        paragraphs.forEach(p -> body.append(p).append("\n\n"));
        body.append("Kind regards,\nSupport Team");
        out.put("body", body.toString());
        out.put("citations", new ArrayList<>(cites));
        out.put("needs_human", false);
        return out;
    }

    /** Adds a statement only if its supporting document was supplied as evidence. */
    private record Builder(Set<String> evidence, Set<String> cites, List<String> paragraphs) {
        void say(String docId, String text) {
            if (evidence.contains(docId)) {
                paragraphs.add(text);
                cites.add(docId);
            }
        }
    }

    // ------------------------------------------------------------------ helpers
    private static boolean allNonReturnable(Map<String, Object> order) {
        if (!(order.get("items") instanceof List<?> items) || items.isEmpty()) {
            return false;
        }
        for (Object o : items) {
            Map<String, Object> item = asMap(o);
            String cat = str(item.get("category")).toLowerCase();
            boolean nonReturnable = Boolean.TRUE.equals(item.get("final_sale"))
                    || cat.equals("software") || cat.equals("download") || cat.equals("gift_card");
            if (!nonReturnable) {
                return false;
            }
        }
        return true;
    }

    private static boolean pastReturnWindow(Map<String, Object> ticket, Map<String, Object> order) {
        try {
            String created = str(ticket.get("created_at"));
            String until = str(order.get("eligible_return_until"));
            String delivered = str(order.get("delivered_at"));
            LocalDate day = OffsetDateTime.parse(created).toLocalDate();
            if (!until.isBlank()) {
                return day.isAfter(LocalDate.parse(until));
            }
            return !delivered.isBlank() && day.isAfter(LocalDate.parse(delivered).plusDays(7));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean flag(Map<String, Object> facts, String key) {
        return Boolean.TRUE.equals(facts.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
