package com.trustdesk.domain;

import com.trustdesk.domain.converter.InstantConverter;
import com.trustdesk.domain.converter.MapListConverter;
import com.trustdesk.domain.converter.ObjectMapConverter;
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
import java.util.Map;

/**
 * Minimal AI run trace (spec Must Have: ticket, run type, retrieved doc ids, tool actions, guardrail result,
 * final status). Latency/model/prompt-version/token/cost are Good To Have and intentionally absent.
 */
@Entity
@Table(name = "agent_runs")
public class AgentRun {
    @Id @Column(length = 64)
    private String runId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private AgentRunType runType;
    @Column(nullable = false, length = 32) private String status;
    @Convert(converter = StringListConverter.class) @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> retrievedDocIds;
    @Convert(converter = MapListConverter.class) @Column(nullable = false, columnDefinition = "TEXT")
    private List<Map<String, Object>> toolCalls;
    @Convert(converter = ObjectMapConverter.class) @Column(nullable = false, columnDefinition = "TEXT")
    private Map<String, Object> guardrailResults;
    @Convert(converter = InstantConverter.class) @Column(nullable = false, length = 40)
    private Instant createdAt;

    protected AgentRun() {}

    public AgentRun(String runId, Ticket ticket, AgentRunType runType, String status, List<String> retrievedDocIds,
                    List<Map<String, Object>> toolCalls, Map<String, Object> guardrailResults) {
        this.runId = runId;
        this.ticket = ticket;
        this.runType = runType;
        this.status = status;
        this.retrievedDocIds = retrievedDocIds;
        this.toolCalls = toolCalls;
        this.guardrailResults = guardrailResults;
        this.createdAt = Instant.now();
    }

    public String getRunId() { return runId; }
    public Ticket getTicket() { return ticket; }
    public AgentRunType getRunType() { return runType; }
    public String getStatus() { return status; }
    public List<String> getRetrievedDocIds() { return retrievedDocIds; }
    public List<Map<String, Object>> getToolCalls() { return toolCalls; }
    public Map<String, Object> getGuardrailResults() { return guardrailResults; }
    public Instant getCreatedAt() { return createdAt; }
}
