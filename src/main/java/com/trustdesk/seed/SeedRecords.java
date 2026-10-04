package com.trustdesk.seed;

import com.trustdesk.domain.OrderItem;
import java.util.List;
import java.util.Map;

/**
 * Shapes of the provided seed files. Dates stay as text here and are parsed by the service so original
 * offsets are preserved. {@link SeedTicket} intentionally has NO expected_* components: those labels are
 * evaluation-only and must never be loaded into the application's data path.
 */
public final class SeedRecords {
    private SeedRecords() {}

    public record SeedCustomer(String customerId, String name, String email, String tier, String country,
                               String createdAt, boolean verified, List<String> tags) {}

    public record SeedOrder(String orderId, String customerId, String status, String placedAt, String deliveredAt,
                            String eligibleReturnUntil, long total, String currency, String paymentStatus,
                            String trackingNumber, List<OrderItem> items) {}

    public record SeedTicket(String ticketId, String customerId, String orderId, String channel, String subject,
                             String body, String createdAt, String status) {}

    public record SeedToolAction(String toolName, String description, String riskLevel,
                                 boolean requiresHumanApproval, List<String> allowedCategories,
                                 List<String> requiredFields, Integer maxAmountInr) {}

    public record SeedEvalCase(String caseId, String ticketId, String input, Map<String, Object> expected) {}

    public record SeedDocument(String docId, String title, String sourcePath, String version, String audience,
                               String content, String checksum) {}

    public record SeedBundle(List<SeedCustomer> customers, List<SeedOrder> orders, List<SeedTicket> tickets,
                             List<SeedToolAction> toolActions, List<SeedEvalCase> evalCases,
                             List<SeedDocument> documents) {}
}
