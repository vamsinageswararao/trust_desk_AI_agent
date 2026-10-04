package com.trustdesk.service;

import com.trustdesk.agent.DraftEngine;
import com.trustdesk.agent.DraftOutcome;
import com.trustdesk.agent.TicketCategory;
import com.trustdesk.agent.TicketContext;
import com.trustdesk.agent.TicketPriority;
import com.trustdesk.agent.TriageDecision;
import com.trustdesk.api.dto.WorkflowViews.CitationView;
import com.trustdesk.api.dto.WorkflowViews.DraftView;
import com.trustdesk.common.Ids;
import com.trustdesk.common.NotFoundException;
import com.trustdesk.domain.AgentRun;
import com.trustdesk.domain.AgentRunType;
import com.trustdesk.domain.DraftReply;
import com.trustdesk.domain.DraftStatus;
import com.trustdesk.domain.KnowledgeDocument;
import com.trustdesk.domain.Ticket;
import com.trustdesk.guardrails.GuardFlag;
import com.trustdesk.guardrails.InputGuard;
import com.trustdesk.repository.AgentRunRepository;
import com.trustdesk.repository.DraftReplyRepository;
import com.trustdesk.repository.KnowledgeDocumentRepository;
import com.trustdesk.repository.TicketRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DraftReplyService {

    private final TicketRepository tickets;
    private final DraftReplyRepository drafts;
    private final AgentRunRepository runRepo;
    private final KnowledgeDocumentRepository documents;
    private final AgentContextFactory factory;
    private final TriageService triageService;
    private final DraftEngine engine;
    private final AgentRunService runs;

    public DraftReplyService(TicketRepository tickets, DraftReplyRepository drafts, AgentRunRepository runRepo,
                             KnowledgeDocumentRepository documents, AgentContextFactory factory,
                             TriageService triageService, DraftEngine engine, AgentRunService runs) {
        this.tickets = tickets;
        this.drafts = drafts;
        this.runRepo = runRepo;
        this.documents = documents;
        this.factory = factory;
        this.triageService = triageService;
        this.engine = engine;
        this.runs = runs;
    }

    public DraftView generate(String ticketId) {
        Ticket t = tickets.findDetailedByTicketId(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
        if (t.getTriageCategory() == null) {
            triageService.triage(ticketId); // same transaction and managed entity: triage fields are now set
        }
        TicketContext ctx = factory.context(t);
        Set<GuardFlag> flags = InputGuard.analyze(ctx.text());
        DraftOutcome o = engine.draft(ctx, storedTriage(t), flags, factory.catalog());

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        o.recommendedActions().forEach(a -> toolCalls.add(a.toMap()));
        for (Map<String, Object> b : o.blockedRecommendations()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tool_name", b.get("tool_name"));
            m.put("status", "blocked");
            m.put("code", b.get("code"));
            m.put("reason", b.get("reason"));
            toolCalls.add(m);
        }
        Map<String, Object> guard = new LinkedHashMap<>(o.guardrail().toMap());
        guard.put("draft_status", o.status());
        guard.put("requires_human_handling", o.requiresHumanHandling());
        guard.put("excluded_documents", o.excluded().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("doc_id", e.docId());
            m.put("reason", e.reason());
            return m;
        }).toList());
        guard.put("explanation", o.explanation());

        AgentRun run = runs.record(t, AgentRunType.DRAFT_REPLY, o.status(), o.retrievedDocIds(), toolCalls, guard);
        DraftReply d = drafts.save(new DraftReply(Ids.next("draft"), t, DraftStatus.GENERATED, o.body(),
                o.citationIds(), run.getRunId()));
        return view(d, run);
    }

    @Transactional(readOnly = true)
    public List<DraftView> forTicket(String ticketId) {
        if (!tickets.existsById(ticketId)) {
            throw new NotFoundException("Ticket not found: " + ticketId);
        }
        return drafts.findByTicket_TicketId(ticketId).stream()
                .sorted(Comparator.comparing(DraftReply::getCreatedAt).thenComparing(DraftReply::getDraftId))
                .map(d -> view(d, runRepo.findById(d.getAgentRunId()).orElse(null))).toList();
    }

    private static TriageDecision storedTriage(Ticket t) {
        return new TriageDecision(TicketCategory.parse(t.getTriageCategory()).orElse(TicketCategory.GENERAL),
                TicketPriority.parse(t.getTriagePriority()).orElse(TicketPriority.MEDIUM),
                t.getTriageSentiment() == null ? "neutral" : t.getTriageSentiment(),
                Boolean.TRUE.equals(t.getTriageShouldEscalate()),
                t.getTriageReason() == null ? "" : t.getTriageReason(), "stored", List.of());
    }

    private DraftView view(DraftReply d, AgentRun run) {
        List<CitationView> citations = new ArrayList<>();
        for (String id : d.getCitations()) {
            KnowledgeDocument doc = documents.findById(id).orElse(null);
            citations.add(doc == null ? new CitationView(id, null, null, null)
                    : new CitationView(doc.getDocId(), doc.getTitle(), doc.getVersion(), doc.getSourcePath()));
        }
        List<Map<String, Object>> recommended = new ArrayList<>();
        List<Map<String, Object>> blocked = new ArrayList<>();
        Map<String, Object> guard = Map.of();
        if (run != null) {
            for (Map<String, Object> call : run.getToolCalls()) {
                ("blocked".equals(call.get("status")) ? blocked : recommended).add(call);
            }
            guard = run.getGuardrailResults();
        }
        return new DraftView(d.getDraftId(), d.getTicket().getTicketId(), d.getStatus().name().toLowerCase(Locale.ROOT),
                d.getBody(), citations, recommended, blocked, guard, Boolean.TRUE.equals(guard.get("requires_human_handling")),
                d.getAgentRunId(), d.getCreatedAt().toString());
    }
}
