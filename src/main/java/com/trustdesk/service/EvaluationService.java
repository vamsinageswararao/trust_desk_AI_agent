package com.trustdesk.service;

import com.trustdesk.agent.ActionPolicy;
import com.trustdesk.agent.ActionRecommender;
import com.trustdesk.agent.DraftEngine;
import com.trustdesk.agent.DraftOutcome;
import com.trustdesk.agent.EvalScorer;
import com.trustdesk.agent.RecommendedAction;
import com.trustdesk.agent.ToolSpec;
import com.trustdesk.agent.TicketContext;
import com.trustdesk.agent.TriageDecision;
import com.trustdesk.agent.TriageEngine;
import com.trustdesk.api.dto.WorkflowViews.EvalRunView;
import com.trustdesk.common.Ids;
import com.trustdesk.common.NotFoundException;
import com.trustdesk.common.RequestValidationException;
import com.trustdesk.domain.AgentRunType;
import com.trustdesk.domain.EvalCase;
import com.trustdesk.domain.EvalRun;
import com.trustdesk.domain.Ticket;
import com.trustdesk.guardrails.GuardFlag;
import com.trustdesk.guardrails.InputGuard;
import com.trustdesk.repository.EvalCaseRepository;
import com.trustdesk.repository.EvalRunRepository;
import com.trustdesk.repository.TicketRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the seeded eval cases through the SAME engines the API uses. The expected labels are read only by
 * {@link EvalScorer} after the engines have produced their output; they are never fed to triage or drafting.
 * Evaluation is read-only with respect to tickets/drafts (no triage written to tickets, no drafts stored); it
 * stores one EVAL_CASE trace per case plus the eval-run summary.
 */
@Service
@Transactional
public class EvaluationService {

    private final EvalCaseRepository cases;
    private final EvalRunRepository evalRuns;
    private final TicketRepository tickets;
    private final AgentContextFactory factory;
    private final TriageEngine triageEngine;
    private final DraftEngine draftEngine;
    private final AgentRunService runs;
    private final ActionPolicy policy = new ActionPolicy();

    public EvaluationService(EvalCaseRepository cases, EvalRunRepository evalRuns, TicketRepository tickets,
                             AgentContextFactory factory, TriageEngine triageEngine, DraftEngine draftEngine,
                             AgentRunService runs) {
        this.cases = cases;
        this.evalRuns = evalRuns;
        this.tickets = tickets;
        this.factory = factory;
        this.triageEngine = triageEngine;
        this.draftEngine = draftEngine;
        this.runs = runs;
    }

    public EvalRunView run(List<String> caseIds) {
        Instant started = Instant.now(); // audit timestamp only
        List<EvalCase> selected = cases.findAll().stream().sorted(Comparator.comparing(EvalCase::getCaseId)).toList();
        if (caseIds != null && !caseIds.isEmpty()) {
            List<String> known = selected.stream().map(EvalCase::getCaseId).toList();
            for (String id : caseIds) {
                if (!known.contains(id)) {
                    throw new RequestValidationException("caseIds", "unknown case id: " + id);
                }
            }
            selected = selected.stream().filter(c -> caseIds.contains(c.getCaseId())).toList();
        }
        Map<String, ToolSpec> catalog = factory.catalog();
        List<Map<String, Object>> results = new ArrayList<>();
        for (EvalCase ec : selected) {
            results.add(evaluate(ec, catalog));
        }
        Map<String, Object> metrics = EvalScorer.metrics(results);
        EvalRun saved = evalRuns.save(new EvalRun(Ids.next("eval_run"), started, Instant.now(), results.size(), metrics, results));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public EvalRunView get(String evalRunId) {
        return evalRuns.findById(evalRunId).map(EvaluationService::view)
                .orElseThrow(() -> new NotFoundException("Eval run not found: " + evalRunId));
    }

    @Transactional(readOnly = true)
    public List<EvalRunView> list() {
        return evalRuns.findAll().stream()
                .sorted(Comparator.comparing(EvalRun::getStartedAt).thenComparing(EvalRun::getEvalRunId).reversed())
                .map(EvaluationService::view).toList();
    }

    private Map<String, Object> evaluate(EvalCase ec, Map<String, ToolSpec> catalog) {
        Ticket t = tickets.findDetailedByTicketId(ec.getTicket().getTicketId())
                .orElseThrow(() -> new NotFoundException("Ticket not found for eval case " + ec.getCaseId()));
        TicketContext ctx = factory.context(t);
        Set<GuardFlag> flags = InputGuard.analyze(ctx.text());
        TriageDecision triage = triageEngine.triage(ctx, flags);
        DraftOutcome draft = draftEngine.draft(ctx, triage, flags, catalog);

        Map<String, Boolean> blocked = new LinkedHashMap<>();
        Object disallowed = ec.getExpected().get("disallowed_actions");
        if (disallowed instanceof List<?> tools) {
            for (Object o : tools) {
                String tool = String.valueOf(o);
                ToolSpec spec = catalog.get(tool);
                // probe with a complete, consistent payload so any refusal comes from policy semantics, not missing fields
                blocked.put(tool, spec != null && !policy.evaluate(spec, ctx, triage.category(), flags,
                        ActionRecommender.payloadFor(tool, ctx, "evaluation probe", triage, flags)).allowed());
            }
        }
        EvalScorer.Observed observed = new EvalScorer.Observed(triage.category(), triage.priority(),
                draft.requiresHumanHandling() || triage.shouldEscalate(), draft.citationIds(),
                draft.recommendedActions().stream().map(RecommendedAction::toolName).toList(), blocked,
                flags.stream().map(Enum::name).sorted().toList());
        Map<String, Object> result = EvalScorer.scoreCase(ec.getCaseId(), t.getTicketId(), ec.getExpected(), observed);

        Map<String, Object> guard = new LinkedHashMap<>(draft.guardrail().toMap());
        guard.put("case_id", ec.getCaseId());
        guard.put("passed", result.get("passed"));
        guard.put("explanation", draft.explanation());
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        draft.recommendedActions().forEach(a -> toolCalls.add(a.toMap()));
        runs.record(t, AgentRunType.EVAL_CASE, Boolean.TRUE.equals(result.get("passed")) ? "passed" : "failed",
                draft.retrievedDocIds(), toolCalls, guard);
        return result;
    }

    private static EvalRunView view(EvalRun r) {
        List<Map<String, Object>> adversarial = new ArrayList<>();
        for (Map<String, Object> c : r.getCaseResults()) {
            if (c.get("guard_flags") instanceof List<?> flags && flags.stream().anyMatch(f -> Set.of(
                    GuardFlag.PROMPT_INJECTION.name(), GuardFlag.SECRET_EXTRACTION.name(),
                    GuardFlag.IDENTITY_BYPASS.name(), GuardFlag.HIDDEN_ACTION_REQUEST.name()).contains(String.valueOf(f)))) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("case_id", c.get("case_id"));
                a.put("ticket_id", c.get("ticket_id"));
                a.put("safe", Boolean.TRUE.equals(c.get("passed")));
                a.put("flags", c.get("guard_flags"));
                adversarial.add(a);
            }
        }
        return new EvalRunView(r.getEvalRunId(), r.getStartedAt().toString(),
                r.getCompletedAt() == null ? null : r.getCompletedAt().toString(), r.getTotalCases(), r.getMetrics(),
                r.getCaseResults(), adversarial);
    }
}
