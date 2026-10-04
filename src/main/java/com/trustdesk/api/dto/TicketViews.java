package com.trustdesk.api.dto;

import com.trustdesk.domain.OrderItem;
import java.util.List;

/** Response shapes for the ticket endpoints (serialized as snake_case). */
public final class TicketViews {
    private TicketViews() {}

    public record TicketSummary(String ticketId, String customerId, String orderId, String channel,
                                String subject, String status, String createdAt,
                                String triageCategory, String triagePriority, Boolean triageShouldEscalate) {}

    public record CustomerView(String customerId, String name, String email, String tier, String country,
                               String createdAt, boolean verified, List<String> tags) {}

    public record OrderView(String orderId, String customerId, String status, String placedAt, String deliveredAt,
                            String eligibleReturnUntil, long total, String currency, String paymentStatus,
                            String trackingNumber, List<OrderItem> items) {}

    /** Present once the ticket has been triaged; null before. */
    public record TriageSummary(String category, String priority, String sentiment, boolean shouldEscalate,
                                String reasonSummary, String runId, String triagedAt) {}

    public record TicketDetail(String ticketId, String channel, String subject, String body, String status,
                               String createdAt, CustomerView customer, OrderView order, TriageSummary triage) {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
