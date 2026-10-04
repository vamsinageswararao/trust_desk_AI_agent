package com.trustdesk.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.ai.MockAiProvider;
import com.trustdesk.guardrails.InputGuard;
import org.junit.jupiter.api.Test;

class TriageEngineTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private TriageDecision run(com.trustdesk.ai.AiProvider provider, TicketContext ctx) {
        return new TriageEngine(provider, mapper).triage(ctx, InputGuard.analyze(ctx.text()));
    }

    private static final String VALID = "{\"category\":\"refund\",\"priority\":\"medium\",\"sentiment\":\"frustrated\","
            + "\"should_escalate\":false,\"reason_summary\":\"Damaged item\"}";

    @Test
    void validModelOutputIsAccepted() {
        TriageDecision d = run(new TestSupport.Scripted(VALID), TestContexts.damagedEarbuds());
        assertEquals(TicketCategory.REFUND, d.category());
        assertEquals(TicketPriority.MEDIUM, d.priority());
        assertFalse(d.shouldEscalate());
        assertEquals("model", d.source());
    }

    @Test
    void jsonInsideACodeFenceIsAccepted() {
        TriageDecision d = run(new TestSupport.Scripted("```json\n" + VALID + "\n```"), TestContexts.damagedEarbuds());
        assertEquals("model", d.source());
    }

    @Test
    void malformedOutputFailsSafeAndEscalates() {
        for (String bad : new String[] {"I think this is a refund!", "", "{not json", "[1,2,3]", "{}"}) {
            TriageDecision d = run(new TestSupport.Scripted(bad), TestContexts.damagedEarbuds());
            assertEquals("fallback", d.source(), bad);
            assertTrue(d.shouldEscalate(), bad);
            assertEquals(TicketCategory.GENERAL, d.category(), bad);
        }
    }

    @Test
    void unsupportedEnumValuesAreRejected() {
        String badCategory = VALID.replace("\"refund\"", "\"hacking\"");
        String badPriority = VALID.replace("\"medium\"", "\"critical\"");
        assertEquals("fallback", run(new TestSupport.Scripted(badCategory), TestContexts.damagedEarbuds()).source());
        assertEquals("fallback", run(new TestSupport.Scripted(badPriority), TestContexts.damagedEarbuds()).source());
    }

    @Test
    void escalationMustBeARealBoolean() {
        String text = VALID.replace("\"should_escalate\":false", "\"should_escalate\":\"no\"");
        assertEquals("fallback", run(new TestSupport.Scripted(text), TestContexts.damagedEarbuds()).source());
        String missing = VALID.replace("\"should_escalate\":false,", "");
        assertEquals("fallback", run(new TestSupport.Scripted(missing), TestContexts.damagedEarbuds()).source());
    }

    @Test
    void providerFailureFailsSafe() {
        TriageDecision d = run(TestSupport.throwing(), TestContexts.damagedEarbuds());
        assertEquals("fallback", d.source());
        assertTrue(d.shouldEscalate());
    }

    @Test
    void extraModelFieldsCannotTriggerAnything() {
        String sneaky = VALID.replace("}", ",\"tool\":\"issue_coupon\",\"execute\":true}");
        TriageDecision d = run(new TestSupport.Scripted(sneaky), TestContexts.damagedEarbuds());
        assertEquals("model", d.source());
        assertEquals(TicketCategory.REFUND, d.category());
    }

    @Test
    void aModelCannotTalkTheApplicationOutOfEscalatingAnInjection() {
        String lenient = "{\"category\":\"general\",\"priority\":\"low\",\"sentiment\":\"neutral\",\"should_escalate\":false,"
                + "\"reason_summary\":\"fine\"}";
        TriageDecision d = run(new TestSupport.Scripted(lenient), TestContexts.couponInjection());
        assertTrue(d.shouldEscalate());
        assertTrue(d.priority().ordinal() >= TicketPriority.MEDIUM.ordinal());
    }

    @Test
    void safetyIssuesAreForcedToUrgentEscalation() {
        TicketContext c = TestContexts.of("Battery", "My tablet battery has started swelling", "2026-07-01T15:30:00+05:30");
        String lenient = "{\"category\":\"warranty\",\"priority\":\"low\",\"should_escalate\":false}";
        TriageDecision d = run(new TestSupport.Scripted(lenient), c);
        assertEquals(TicketPriority.URGENT, d.priority());
        assertTrue(d.shouldEscalate());
    }

    @Test
    void mockProviderClassifiesFromTicketTextAndContext() {
        MockAiProvider mock = new MockAiProvider(mapper);
        assertEquals(TicketCategory.REFUND, run(mock, TestContexts.damagedEarbuds()).category());
        TicketContext finalSale = TestContexts.finalSaleSoftware();
        TriageDecision d = run(mock, finalSale);
        assertEquals(TicketCategory.REFUND, d.category());
        assertEquals(TicketPriority.LOW, d.priority());
        TriageDecision inj = run(mock, TestContexts.couponInjection());
        assertEquals(TicketCategory.GENERAL, inj.category());
        assertTrue(inj.shouldEscalate());
    }
}
