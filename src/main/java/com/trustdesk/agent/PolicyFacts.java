package com.trustdesk.agent;

import com.trustdesk.domain.OrderItem;
import com.trustdesk.guardrails.GuardFlag;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Facts computed by the APPLICATION (never by the model) from ticket/order context. All date logic uses the
 * ticket's own {@code created_at}; the wall clock is never consulted.
 */
public record PolicyFacts(boolean hasOrder, boolean returnWindowOpen, boolean allItemsNonReturnable,
                          boolean warrantyCovered, boolean damageReported, boolean staleTrackingReported,
                          boolean urgentTravel, boolean safetyIssue, boolean identityBypass, boolean injection,
                          boolean secretRequest, boolean hiddenAction, boolean couponRequested) {

    private static final Pattern DAMAGE = Pattern.compile(
            "damag|crack|broken|defect|faulty|not working|stopped working|dead on arrival|torn|dent", Pattern.CASE_INSENSITIVE);
    private static final Pattern STALE = Pattern.compile(
            "tracking|no movement|not moved|hasn.t moved|stuck in transit|lost (?:my )?(?:package|parcel|order)"
                    + "|not arrived|hasn.t arrived|not received|haven.t received", Pattern.CASE_INSENSITIVE);
    private static final Pattern URGENT = Pattern.compile(
            "travel|trip|flight|urgent|asap|time-sensitive|deadline|need (?:it|them|this) by", Pattern.CASE_INSENSITIVE);
    private static final Set<String> NON_RETURNABLE_CATEGORIES = Set.of("software", "download", "gift_card");

    public static PolicyFacts of(TicketContext c, Set<GuardFlag> flags) {
        String text = c.text();
        LocalDate ticketDay = c.createdAt() == null ? null : c.createdAt().toLocalDate(); // ticket's own offset
        boolean windowOpen = false;
        if (c.hasOrder() && ticketDay != null) {
            LocalDate until = c.eligibleReturnUntil() != null ? c.eligibleReturnUntil()
                    : (c.deliveredAt() != null ? c.deliveredAt().plusDays(7) : null);
            windowOpen = until != null && !ticketDay.isAfter(until);
        }
        List<OrderItem> items = c.items() == null ? List.of() : c.items();
        boolean allNonReturnable = !items.isEmpty() && items.stream().allMatch(
                i -> i.finalSale() || NON_RETURNABLE_CATEGORIES.contains(lower(i.category())));
        return new PolicyFacts(c.hasOrder(), windowOpen, allNonReturnable, warrantyCovered(c, ticketDay, items),
                DAMAGE.matcher(text).find(), STALE.matcher(text).find(), URGENT.matcher(text).find(),
                flags.contains(GuardFlag.SAFETY_ISSUE), flags.contains(GuardFlag.IDENTITY_BYPASS),
                flags.contains(GuardFlag.PROMPT_INJECTION), flags.contains(GuardFlag.SECRET_EXTRACTION),
                flags.contains(GuardFlag.HIDDEN_ACTION_REQUEST), flags.contains(GuardFlag.COUPON_REQUEST));
    }

    /** 12 months from delivery (KB-WARRANTY-001); +6 for gold on eligible hardware (not software, not accessories < INR 3000). */
    private static boolean warrantyCovered(TicketContext c, LocalDate ticketDay, List<OrderItem> items) {
        if (c.deliveredAt() == null || ticketDay == null || items.isEmpty()) {
            return false;
        }
        boolean hardware = items.stream().anyMatch(i -> !NON_RETURNABLE_CATEGORIES.contains(lower(i.category())));
        if (!hardware) {
            return false;
        }
        boolean cheapAccessory = items.stream().allMatch(i -> "accessory".equals(lower(i.category())))
                && c.orderTotal() < 3000;
        int months = 12 + ("gold".equals(lower(c.customerTier())) && !cheapAccessory ? 6 : 0);
        return !ticketDay.isAfter(c.deliveredAt().plusMonths(months));
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
