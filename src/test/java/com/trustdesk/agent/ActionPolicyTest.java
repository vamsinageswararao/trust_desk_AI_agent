package com.trustdesk.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trustdesk.guardrails.GuardFlag;
import com.trustdesk.guardrails.InputGuard;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActionPolicyTest {

    private final ActionPolicy policy = new ActionPolicy();
    private final Map<String, ToolSpec> catalog = TestSupport.catalog();

    private ActionPolicy.Decision check(String tool, TicketContext ctx, TicketCategory category) {
        Set<GuardFlag> flags = InputGuard.analyze(ctx.text());
        TriageDecision t = TestSupport.triage(category == null ? TicketCategory.GENERAL : category, TicketPriority.MEDIUM, false);
        return policy.evaluate(catalog.get(tool), ctx, category, flags, ActionRecommender.payloadFor(tool, ctx, "test", t, flags));
    }

    @Test
    void refundReviewIsAllowedInsideTheReturnWindow() {
        assertTrue(check("start_refund_review", TestContexts.damagedEarbuds(), TicketCategory.REFUND).allowed());
    }

    @Test
    void returnWindowIsEvaluatedFromTicketCreatedAtNotTheClock() {
        TicketContext open = TestContexts.withCreatedAt(TestContexts.damagedEarbuds(), "2026-07-01T23:30:00+05:30");
        TicketContext closed = TestContexts.withCreatedAt(TestContexts.damagedEarbuds(), "2026-07-02T00:10:00+05:30");
        assertTrue(check("start_refund_review", open, TicketCategory.REFUND).allowed());
        assertEquals("RETURN_WINDOW_CLOSED", check("start_refund_review", closed, TicketCategory.REFUND).code());
        assertEquals("RETURN_WINDOW_CLOSED", check("create_replacement_order", closed, TicketCategory.REFUND).code());
    }

    @Test
    void finalSaleItemsCannotBeRefundedOrReplaced() {
        assertEquals("FINAL_SALE", check("start_refund_review", TestContexts.finalSaleSoftware(), TicketCategory.REFUND).code());
        assertEquals("FINAL_SALE", check("create_replacement_order", TestContexts.finalSaleSoftware(), TicketCategory.REFUND).code());
    }

    @Test
    void toolMustBeAllowedForTheTicketCategory() {
        assertEquals("CATEGORY_NOT_ALLOWED", check("issue_coupon", TestContexts.damagedEarbuds(), TicketCategory.REFUND).code());
        assertEquals("CATEGORY_NOT_ALLOWED", check("start_refund_review", TestContexts.damagedEarbuds(), TicketCategory.SHIPPING).code());
        assertEquals("NOT_TRIAGED", check("start_refund_review", TestContexts.damagedEarbuds(), null).code());
    }

    @Test
    void adversarialTicketsOnlyAllowEscalation() {
        TicketContext c = TestContexts.couponInjection();
        assertEquals("ADVERSARIAL_TICKET", check("issue_coupon", c, TicketCategory.GENERAL).code());
        assertTrue(check("escalate_to_human", c, TicketCategory.GENERAL).allowed());
    }

    @Test
    void accountLockIsNeverAutomatable() {
        TicketContext c = TestContexts.of("Locked", "my account looks hacked", "2026-07-02T10:00:00+05:30");
        assertEquals("NOT_AUTOMATABLE", check("lock_account", c, TicketCategory.ACCOUNT_SECURITY).code());
    }

    @Test
    void payloadMustBelongToTheTicket() {
        TicketContext c = TestContexts.damagedEarbuds();
        Set<GuardFlag> flags = InputGuard.analyze(c.text());
        Map<String, Object> p = new LinkedHashMap<>(ActionRecommender.payloadFor("start_refund_review", c, "x",
                TestSupport.triage(TicketCategory.REFUND, TicketPriority.MEDIUM, false), flags));
        p.put("order_id", "ord_9999");
        assertEquals("ORDER_MISMATCH", policy.evaluate(catalog.get("start_refund_review"), c, TicketCategory.REFUND, flags, p).code());
    }

    @Test
    void missingFieldsAndBadAmountsAreRejected() {
        TicketContext c = TestContexts.damagedEarbuds();
        Set<GuardFlag> flags = InputGuard.analyze(c.text());
        Map<String, Object> p = new LinkedHashMap<>(ActionRecommender.payloadFor("start_refund_review", c, "x",
                TestSupport.triage(TicketCategory.REFUND, TicketPriority.MEDIUM, false), flags));
        p.put("amount", 8999 + 1);
        assertEquals("INVALID_AMOUNT", policy.evaluate(catalog.get("start_refund_review"), c, TicketCategory.REFUND, flags, p).code());
        p.put("amount", -5);
        assertEquals("INVALID_AMOUNT", policy.evaluate(catalog.get("start_refund_review"), c, TicketCategory.REFUND, flags, p).code());
        p.put("amount", "abc");
        assertEquals("INVALID_AMOUNT", policy.evaluate(catalog.get("start_refund_review"), c, TicketCategory.REFUND, flags, p).code());
        p.remove("idempotency_key");
        assertEquals("MISSING_FIELD", policy.evaluate(catalog.get("start_refund_review"), c, TicketCategory.REFUND, flags, p).code());
    }

    @Test
    void couponAmountIsCappedByTheCatalogLimit() {
        TicketContext c = TestContexts.of("Late parcel", "my parcel is late", "2026-07-02T10:00:00+05:30");
        Set<GuardFlag> flags = InputGuard.analyze(c.text());
        Map<String, Object> p = new LinkedHashMap<>(ActionRecommender.payloadFor("issue_coupon", c, "x",
                TestSupport.triage(TicketCategory.SHIPPING, TicketPriority.MEDIUM, false), flags));
        assertTrue(policy.evaluate(catalog.get("issue_coupon"), c, TicketCategory.SHIPPING, flags, p).allowed());
        p.put("amount", 5000);
        assertEquals("COUPON_LIMIT", policy.evaluate(catalog.get("issue_coupon"), c, TicketCategory.SHIPPING, flags, p).code());
    }

    @Test
    void replacementIsBlockedWhileASafetyIssueIsOpen() {
        TicketContext c = TestContexts.of("Battery", "the battery is swelling after a few months", "2026-07-02T10:00:00+05:30");
        assertEquals("SAFETY_ESCALATION_FIRST", check("create_replacement_order", c, TicketCategory.WARRANTY).code());
    }
}
