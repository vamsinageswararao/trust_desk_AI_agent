package com.trustdesk.guardrails;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic detector for adversarial or safety-relevant content in UNTRUSTED text (customer messages).
 * It only raises flags; flags can only make the system MORE cautious (escalate, block actions), never less.
 */
public final class InputGuard {

    private static final int CI = Pattern.CASE_INSENSITIVE;

    private static final Pattern INJECTION = Pattern.compile(
            "(?:ignore|disregard|forget|override)\\W+(?:\\w+\\W+){0,4}?(?:instructions?|rules?|polic(?:y|ies)|guidelines?|checks?)"
                    + "|system\\s+override|you\\s+are\\s+now\\s+(?:allowed|able|free|permitted)|new\\s+instructions"
                    + "|developer\\s+mode|jailbreak|polic(?:y|ies)\\s+(?:has|have)\\s+changed|approve\\s+all\\s+refunds", CI);

    private static final Pattern SECRET = Pattern.compile(
            "system\\s+prompt|hidden\\s+(?:system\\s+)?(?:prompt|instructions?)|api[\\s_-]?keys?|internal\\s+notes?"
                    + "|(?:reveal|print|show|display|dump|leak)\\s+(?:me\\s+)?(?:your|the)\\s+(?:instructions?|prompt|configuration|secrets?|credentials?)"
                    + "|secret\\s+key|access\\s+token", CI);

    private static final Pattern IDENTITY_BYPASS = Pattern.compile(
            "(?:ignore|skip|bypass|waive|without|no\\s+need\\s+(?:for|to))\\W+(?:\\w+\\W+){0,3}?"
                    + "(?:identity|verification|verify|id\\s+check|security\\s+check)", CI);

    private static final Pattern HIDDEN_ACTION = Pattern.compile(
            "(?:do\\s+not|don't|dont|never)\\W+(?:\\w+\\W+){0,3}?(?:mention|tell|inform|notify|show|alert)\\W+(?:\\w+\\W+){0,4}?"
                    + "(?:human|reviewer|agent|supervisor|manager|team)|hide\\s+(?:this|it|that)\\b"
                    + "|keep\\s+(?:this|it)\\s+secret|without\\s+(?:telling|informing)", CI);

    private static final Pattern COUPON = Pattern.compile("coupon|voucher|promo\\s*code|discount\\s+code", CI);

    private static final Pattern SAFETY = Pattern.compile(
            "swell(?:ing|ed|s)?|swollen|bulging|overheat\\w*|burning\\s+smell|smell\\w*\\s+(?:of\\s+)?burn\\w*|smoke"
                    + "|exposed\\s+wires?|electric\\s+shock|caught\\s+fire|on\\s+fire", CI);

    private InputGuard() {}

    public static Set<GuardFlag> analyze(String text) {
        Set<GuardFlag> flags = EnumSet.noneOf(GuardFlag.class);
        if (text == null || text.isBlank()) {
            return flags;
        }
        if (INJECTION.matcher(text).find()) flags.add(GuardFlag.PROMPT_INJECTION);
        if (SECRET.matcher(text).find()) flags.add(GuardFlag.SECRET_EXTRACTION);
        if (IDENTITY_BYPASS.matcher(text).find()) flags.add(GuardFlag.IDENTITY_BYPASS);
        if (HIDDEN_ACTION.matcher(text).find()) flags.add(GuardFlag.HIDDEN_ACTION_REQUEST);
        if (COUPON.matcher(text).find()) flags.add(GuardFlag.COUPON_REQUEST);
        if (SAFETY.matcher(text).find()) flags.add(GuardFlag.SAFETY_ISSUE);
        return flags;
    }

    /** True if any flag indicates an attempt to manipulate the system (as opposed to benign signals). */
    public static boolean isAdversarial(Set<GuardFlag> flags) {
        return flags.contains(GuardFlag.PROMPT_INJECTION) || flags.contains(GuardFlag.SECRET_EXTRACTION)
                || flags.contains(GuardFlag.IDENTITY_BYPASS) || flags.contains(GuardFlag.HIDDEN_ACTION_REQUEST);
    }
}
