package com.trustdesk.domain;

import com.trustdesk.domain.converter.InstantConverter;
import com.trustdesk.domain.converter.StringListConverter;
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
import java.util.List;

/** Schema only in Milestone 1 (draft generation arrives in Milestone 3). */
@Entity
@Table(name = "draft_replies")
public class DraftReply {
    @Id @Column(length = 64)
    private String draftId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private DraftStatus status;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Convert(converter = StringListConverter.class) @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> citations;
    @Column(length = 64) private String agentRunId;
    @Convert(converter = InstantConverter.class) @Column(nullable = false, length = 40)
    private Instant createdAt;

    protected DraftReply() {}

    public DraftReply(String draftId, Ticket ticket, DraftStatus status, String body, List<String> citations,
                      String agentRunId) {
        this.draftId = draftId;
        this.ticket = ticket;
        this.status = status;
        this.body = body;
        this.citations = citations;
        this.agentRunId = agentRunId;
        this.createdAt = Instant.now();
    }

    public String getDraftId() { return draftId; }
    public Ticket getTicket() { return ticket; }
    public DraftStatus getStatus() { return status; }
    public String getBody() { return body; }
    public List<String> getCitations() { return citations; }
    public String getAgentRunId() { return agentRunId; }
    public Instant getCreatedAt() { return createdAt; }
}
