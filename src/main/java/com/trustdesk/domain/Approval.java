package com.trustdesk.domain;

import com.trustdesk.domain.converter.InstantConverter;
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

/** Human decision on a tool action or a draft (DATA_MODEL: "action_id or draft_id"). Schema only in Milestone 1. */
@Entity
@Table(name = "approvals")
public class Approval {
    @Id @Column(length = 64)
    private String approvalId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_id")
    private ToolAction action;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id")
    private DraftReply draft;
    @Column(nullable = false, length = 64) private String reviewerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private ApprovalDecision decision;
    @Column(columnDefinition = "TEXT") private String reason;
    @Convert(converter = InstantConverter.class) @Column(nullable = false, length = 40)
    private Instant createdAt;

    protected Approval() {}

    public Approval(String approvalId, ToolAction action, DraftReply draft, String reviewerId,
                    ApprovalDecision decision, String reason) {
        this.approvalId = approvalId;
        this.action = action;
        this.draft = draft;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public String getApprovalId() { return approvalId; }
    public ToolAction getAction() { return action; }
    public DraftReply getDraft() { return draft; }
    public String getReviewerId() { return reviewerId; }
    public ApprovalDecision getDecision() { return decision; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
