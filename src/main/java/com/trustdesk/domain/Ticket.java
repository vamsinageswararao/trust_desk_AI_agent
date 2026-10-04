package com.trustdesk.domain;

import com.trustdesk.domain.converter.InstantConverter;
import com.trustdesk.domain.converter.OffsetDateTimeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Support ticket. The seed-only {@code expected_*} evaluation labels are deliberately NOT mapped here,
 * so they cannot reach the AI path. The triage_* columns are reserved for Milestone 2 (spec: "Store the triage
 * result with the ticket") and stay null until then.
 */
@Entity
@Table(name = "tickets")
public class Ticket {
    @Id @Column(length = 64)
    private String ticketId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    @Column(nullable = false, length = 32) private String channel;
    @Column(nullable = false) private String subject;
    /** Original customer message; never updated (auditability). */
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String body;
    @Convert(converter = OffsetDateTimeConverter.class) @Column(nullable = false, length = 40)
    private OffsetDateTime createdAt;
    @Column(nullable = false, length = 32) private String status;

    // --- reserved for Milestone 2 (triage result) ---
    @Column(length = 32) private String triageCategory;
    @Column(length = 16) private String triagePriority;
    @Column(length = 32) private String triageSentiment;
    private Boolean triageShouldEscalate;
    @Column(columnDefinition = "TEXT") private String triageReason;
    @Column(length = 64) private String triageRunId;
    @Convert(converter = InstantConverter.class) @Column(length = 40)
    private Instant triagedAt;

    protected Ticket() {}

    public Ticket(String ticketId, Customer customer, Order order, String channel, String subject, String body,
                  OffsetDateTime createdAt, String status) {
        this.ticketId = ticketId;
        this.customer = customer;
        this.order = order;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.createdAt = createdAt;
        this.status = status;
    }

    /** Stores the validated triage result. Triage time is an audit timestamp only; policy never reads the clock. */
    public void applyTriage(String category, String priority, String sentiment, boolean shouldEscalate,
                            String reason, String runId) {
        this.triageCategory = category;
        this.triagePriority = priority;
        this.triageSentiment = sentiment;
        this.triageShouldEscalate = shouldEscalate;
        this.triageReason = reason;
        this.triageRunId = runId;
        this.triagedAt = Instant.now();
    }

    public String getTicketId() { return ticketId; }
    public Customer getCustomer() { return customer; }
    public Order getOrder() { return order; }
    public String getChannel() { return channel; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public String getTriageCategory() { return triageCategory; }
    public String getTriagePriority() { return triagePriority; }
    public String getTriageSentiment() { return triageSentiment; }
    public Boolean getTriageShouldEscalate() { return triageShouldEscalate; }
    public String getTriageReason() { return triageReason; }
    public String getTriageRunId() { return triageRunId; }
    public Instant getTriagedAt() { return triagedAt; }
}
