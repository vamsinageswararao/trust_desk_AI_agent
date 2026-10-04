package com.trustdesk.service;

import com.trustdesk.agent.TicketContext;
import com.trustdesk.agent.TriageDecision;
import com.trustdesk.agent.TriageEngine;
import com.trustdesk.api.dto.WorkflowViews.TriageView;
import com.trustdesk.common.NotFoundException;
import com.trustdesk.domain.AgentRun;
import com.trustdesk.domain.AgentRunType;
import com.trustdesk.domain.Ticket;
import com.trustdesk.guardrails.GuardFlag;
import com.trustdesk.guardrails.InputGuard;
import com.trustdesk.repository.TicketRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TriageService {

    private final TicketRepository tickets;
    private final AgentContextFactory factory;
    private final TriageEngine engine;
    private final AgentRunService runs;

    public TriageService(TicketRepository tickets, AgentContextFactory factory, TriageEngine engine, AgentRunService runs) {
        this.tickets = tickets;
        this.factory = factory;
        this.engine = engine;
        this.runs = runs;
    }

    /** Classifies the ticket, stores the validated result on it, and writes a trace. Safe to call repeatedly. */
    public TriageView triage(String ticketId) {
        Ticket t = tickets.findDetailedByTicketId(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
        TicketContext ctx = factory.context(t);
        Set<GuardFlag> flags = InputGuard.analyze(ctx.text());
        TriageDecision d = engine.triage(ctx, flags);

        List<String> violations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (String w : d.warnings()) {
            (w.startsWith("invalid_model_output") || w.startsWith("ai_provider_error") ? violations : notes).add(w);
        }
        notes.add("model output validated (enums, types) before persistence; ticket text treated as untrusted data");
        Map<String, Object> guard = new LinkedHashMap<>();
        guard.put("decision", d.shouldEscalate() ? "escalated" : "passed");
        guard.put("flags", flags.stream().map(Enum::name).sorted().toList());
        guard.put("violations", violations);
        guard.put("notes", notes);
        guard.put("explanation", List.of("category=" + d.category().wire() + ", priority=" + d.priority().wire()
                + ", should_escalate=" + d.shouldEscalate() + ", source=" + d.source() + ": " + d.reasonSummary()));

        AgentRun run = runs.record(t, AgentRunType.TRIAGE, "model".equals(d.source()) ? "completed" : "fallback_escalated",
                List.of(), List.of(), guard);
        t.applyTriage(d.category().wire(), d.priority().wire(), d.sentiment(), d.shouldEscalate(), d.reasonSummary(),
                run.getRunId());
        return new TriageView(t.getTicketId(), d.category().wire(), d.priority().wire(), d.sentiment(), d.shouldEscalate(),
                d.reasonSummary(), d.source(), d.warnings(), run.getRunId(), guard);
    }
}
