package com.trustdesk.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OutputGuardTest {

    private static final List<String> OTHERS = List.of("rahul.mehta@example.com");

    private static List<String> scan(String body) {
        return OutputGuard.scan(body, OTHERS);
    }

    @Test
    void cleanReplyPasses() {
        assertEquals(List.of(), scan("Hi Aisha,\n\nWe can offer a replacement or a refund review. A refund review needs human approval, "
                + "so I can't confirm that any refund has been issued. Kind regards, Support Team"));
    }

    @Test
    void fullCardNumbersAreBlockedButLastFourIsAllowed() {
        assertTrue(scan("Your card 4111 1111 1111 1111 was charged").contains("CARD_NUMBER"));
        assertEquals(List.of(), scan("The card ending in 4242 was charged"));
        assertEquals(List.of(), scan("Reference 4111 1111 1111 1112 is not a card number"));
    }

    @Test
    void secretsAndPromptLeaksAreBlocked() {
        assertTrue(scan("The key is sk-abcdefghijklmnop1234").contains("SECRET_LEAK"));
        assertTrue(scan("api_key: abcd1234efgh").contains("SECRET_LEAK"));
        assertTrue(scan("My instructions say " + OutputGuard.SYSTEM_CANARY).contains("SYSTEM_PROMPT_LEAK"));
    }

    @Test
    void anotherCustomersEmailIsBlocked() {
        assertTrue(scan("Please contact rahul.mehta@example.com for details").contains("OTHER_CUSTOMER_DATA"));
        assertEquals(List.of(), scan("We will write to aisha.rao@example.com"));
    }

    @Test
    void unsupportedPromisesAreBlocked() {
        assertTrue(scan("Good news, your refund has been processed.").contains("UNSUPPORTED_PROMISE"));
        assertTrue(scan("We've issued a coupon for you.").contains("UNSUPPORTED_PROMISE"));
        assertTrue(scan("You will receive a full refund tomorrow.").contains("UNSUPPORTED_PROMISE"));
        assertTrue(scan("We guarantee an instant replacement.").contains("UNSUPPORTED_PROMISE"));
    }

    @Test
    void negatedStatementsAreAllowed() {
        assertEquals(List.of(), scan("I'm not able to promise an instant refund or replacement."));
        assertEquals(List.of(), scan("I can't confirm that any refund has been made until payment operations confirms it."));
        assertEquals(List.of(), scan("Please never send your full card number, CVV, OTP or password."));
    }

    @Test
    void askingForSensitivePaymentDataIsBlocked() {
        assertTrue(scan("Please send us your CVV and OTP to continue.").contains("REQUESTS_SENSITIVE_PAYMENT_DATA"));
        assertTrue(scan("Reply with your full card number.").contains("REQUESTS_SENSITIVE_PAYMENT_DATA"));
    }
}
