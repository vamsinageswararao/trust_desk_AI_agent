package com.trustdesk.agent;

import com.trustdesk.domain.OrderItem;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of everything the agent may look at for one ticket. Built by the service layer from entities;
 * engines depend only on this record. Evaluation labels are never part of it. {@code createdAt} (the ticket's own
 * timestamp and offset) is the only time reference used for policy windows.
 */
public record TicketContext(String ticketId, String channel, String subject, String body, OffsetDateTime createdAt,
                            String customerId, String customerName, String customerEmail, String customerTier,
                            boolean customerVerified, String orderId, String orderStatus, LocalDate deliveredAt,
                            LocalDate eligibleReturnUntil, long orderTotal, String currency, String trackingNumber,
                            List<OrderItem> items, Set<String> otherCustomerEmails) {

    public String text() {
        return (subject == null ? "" : subject) + "\n" + (body == null ? "" : body);
    }

    public boolean hasOrder() {
        return orderId != null;
    }

    public String firstName() {
        if (customerName == null || customerName.isBlank()) {
            return null;
        }
        return customerName.strip().split("\\s+")[0];
    }
}
