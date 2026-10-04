package com.trustdesk.guardrails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans a generated customer reply BEFORE it is stored or shown. Any violation means the reply is replaced by a
 * safe fallback. Negated sentences ("we can't promise an instant refund", "never share your CVV") are allowed.
 */
public final class OutputGuard {

    /** Placed inside system prompts; if it ever appears in a reply the prompt has leaked. */
    public static final String SYSTEM_CANARY = "TD-CANARY-7f3a91";

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final Pattern DIGIT_RUN = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");
    private static final Pattern SECRET = Pattern.compile(
            "sk-[A-Za-z0-9_-]{10,}|AIza[0-9A-Za-z_-]{20,}|gsk_[A-Za-z0-9]{10,}|bearer\\s+[A-Za-z0-9._-]{16,}"
                    + "|-----BEGIN [A-Z ]*PRIVATE KEY-----|api[_ -]?key\\s*[:=]\\s*\\S{6,}", CI);
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern ASKS_FOR_SENSITIVE = Pattern.compile(
            "\\b(?:share|send|provide|give|tell|enter|reply\\s+with|confirm)\\b[^.!?\\n]{0,60}\\b"
                    + "(?:cvv|cvc|otp|one[- ]time\\s+(?:password|code)|password|pin|full\\s+card\\s+number|card\\s+number)\\b", CI);
    private static final Pattern PROMISE = Pattern.compile(
            "(?:has|have|had)\\s+been\\s+(?:refunded|issued|credited|processed|approved|completed)"
                    + "|(?:we(?:'ve|\\s+have)|i(?:'ve|\\s+have))\\s+(?:refunded|issued|credited|processed|approved|sent|created|applied)"
                    + "|refund\\s+(?:is|was)\\s+(?:processed|complete|issued|approved|guaranteed)"
                    + "|(?:will|shall)\\s+be\\s+(?:refunded|credited|replaced)\\b"
                    + "|(?:you\\s+will|you'll)\\s+(?:receive|get)\\s+(?:a|your|an)\\s+(?:full\\s+)?(?:refund|replacement)"
                    + "|instant(?:ly)?\\s+(?:refund|replacement)|guarantee"
                    + "|coupon\\s+(?:has\\s+been|is)\\s+(?:issued|applied|created)|issued\\s+(?:a|your|the)\\s+coupon", CI);
    private static final Pattern NEGATION = Pattern.compile(
            "\\b(?:not|never|cannot|can't|cant|unable|won't|don't|do\\s+not|no)\\b", CI);
    private static final Pattern SENTENCES = Pattern.compile("(?<=[.!?])\\s+|\\n+");

    private OutputGuard() {}

    /**
     * @param otherCustomerEmails e-mail addresses of OTHER customers; their appearance is a data leak
     * @return distinct violation codes (empty = clean)
     */
    public static List<String> scan(String body, Collection<String> otherCustomerEmails) {
        Set<String> violations = new LinkedHashSet<>();
        if (body == null) {
            return List.of();
        }
        if (body.contains(SYSTEM_CANARY)) violations.add("SYSTEM_PROMPT_LEAK");
        if (SECRET.matcher(body).find()) violations.add("SECRET_LEAK");
        if (containsCardNumber(body)) violations.add("CARD_NUMBER");
        String lower = body.toLowerCase(Locale.ROOT);
        if (otherCustomerEmails != null) {
            Matcher m = EMAIL.matcher(body);
            while (m.find()) {
                String found = m.group().toLowerCase(Locale.ROOT);
                for (String other : otherCustomerEmails) {
                    if (other != null && found.equals(other.toLowerCase(Locale.ROOT))) {
                        violations.add("OTHER_CUSTOMER_DATA");
                    }
                }
            }
        }
        for (String sentence : SENTENCES.split(body)) {
            if (NEGATION.matcher(sentence).find()) {
                continue;
            }
            if (ASKS_FOR_SENSITIVE.matcher(sentence).find()) violations.add("REQUESTS_SENSITIVE_PAYMENT_DATA");
            if (PROMISE.matcher(sentence).find()) violations.add("UNSUPPORTED_PROMISE");
        }
        return new ArrayList<>(violations);
    }

    static boolean containsCardNumber(String text) {
        Matcher m = DIGIT_RUN.matcher(text);
        while (m.find()) {
            String digits = m.group().replaceAll("[^0-9]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && luhn(digits)) {
                return true;
            }
        }
        return false;
    }

    private static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
