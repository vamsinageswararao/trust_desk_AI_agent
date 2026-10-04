package com.trustdesk.domain;

import com.trustdesk.domain.converter.InstantConverter;
import com.trustdesk.domain.converter.ObjectMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;

/**
 * Tool action request. Schema only in Milestone 1. The UNIQUE constraint on idempotency_key is the database-level
 * backstop for the idempotency logic that arrives in Milestone 4.
 */
@Entity
@Table(name = "tool_actions")
public class ToolAction {
    @Id @Column(length = 64)
    private String actionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tool_name")
    private ToolCatalogEntry tool;
    @Convert(converter = ObjectMapConverter.class) @Column(nullable = false, columnDefinition = "TEXT")
    private Map<String, Object> payload;
    /** Snapshot of the catalog values at request time. */
    @Column(nullable = false, length = 16) private String riskLevel;
    @Column(nullable = false) private boolean requiresHumanApproval;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private ToolActionStatus status;
    @Column(nullable = false, unique = true, length = 200) private String idempotencyKey;
    @Convert(converter = ObjectMapConverter.class) @Column(columnDefinition = "TEXT")
    private Map<String, Object> executionResult;
    @Convert(converter = InstantConverter.class) @Column(nullable = false, length = 40)
    private Instant createdAt;
    @Convert(converter = InstantConverter.class) @Column(nullable = false, length = 40)
    private Instant updatedAt;

    protected ToolAction() {}

    public ToolAction(String actionId, Ticket ticket, ToolCatalogEntry tool, Map<String, Object> payload,
                      ToolActionStatus status, String idempotencyKey) {
        this.actionId = actionId;
        this.ticket = ticket;
        this.tool = tool;
        this.payload = payload;
        this.riskLevel = tool.getRiskLevel();
        this.requiresHumanApproval = tool.isRequiresHumanApproval();
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markApproved() { this.status = ToolActionStatus.APPROVED; touch(); }

    public void markRejected() { this.status = ToolActionStatus.REJECTED; touch(); }

    public void markExecuted(Map<String, Object> result) {
        this.status = ToolActionStatus.EXECUTED;
        this.executionResult = result;
        touch();
    }

    public void markFailed(Map<String, Object> result) {
        this.status = ToolActionStatus.FAILED;
        this.executionResult = result;
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public String getActionId() { return actionId; }
    public Ticket getTicket() { return ticket; }
    public ToolCatalogEntry getTool() { return tool; }
    public Map<String, Object> getPayload() { return payload; }
    public String getRiskLevel() { return riskLevel; }
    public boolean isRequiresHumanApproval() { return requiresHumanApproval; }
    public ToolActionStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Map<String, Object> getExecutionResult() { return executionResult; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
