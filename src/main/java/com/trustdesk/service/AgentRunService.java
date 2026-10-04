package com.trustdesk.service;

import com.trustdesk.api.dto.WorkflowViews.AgentRunView;
import com.trustdesk.common.Ids;
import com.trustdesk.common.NotFoundException;
import com.trustdesk.domain.AgentRun;
import com.trustdesk.domain.AgentRunType;
import com.trustdesk.domain.Ticket;
import com.trustdesk.repository.AgentRunRepository;
import com.trustdesk.repository.TicketRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists and reads the minimal AI/tool trace ("why did the agent do this?"). */
@Service
public class AgentRunService {

    private final AgentRunRepository runs;
    private final TicketRepository tickets;

    public AgentRunService(AgentRunRepository runs, TicketRepository tickets) {
        this.runs = runs;
        this.tickets = tickets;
    }

    /** Joins the caller's transaction. */
    public AgentRun record(Ticket ticket, AgentRunType type, String status, List<String> retrievedDocIds,
                           List<Map<String, Object>> toolCalls, Map<String, Object> guardrailResults) {
        return runs.save(new AgentRun(Ids.next("run"), ticket, type, status, retrievedDocIds, toolCalls, guardrailResults));
    }

    @Transactional(readOnly = true)
    public AgentRunView get(String runId) {
        return runs.findById(runId).map(AgentRunService::view)
                .orElseThrow(() -> new NotFoundException("Agent run not found: " + runId));
    }

    @Transactional(readOnly = true)
    public List<AgentRunView> forTicket(String ticketId) {
        if (!tickets.existsById(ticketId)) {
            throw new NotFoundException("Ticket not found: " + ticketId);
        }
        return runs.findByTicket_TicketId(ticketId).stream()
                .sorted(Comparator.comparing(AgentRun::getCreatedAt).thenComparing(AgentRun::getRunId))
                .map(AgentRunService::view).toList();
    }

    static AgentRunView view(AgentRun r) {
        return new AgentRunView(r.getRunId(), r.getTicket().getTicketId(), r.getRunType().name().toLowerCase(Locale.ROOT),
                r.getStatus(), r.getRetrievedDocIds(), r.getToolCalls(), r.getGuardrailResults(), r.getCreatedAt().toString());
    }
}
